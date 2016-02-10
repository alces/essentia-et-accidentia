---
layout: post
title: 'Deploying Docker Containers by Ansible'
date: 2016-02-10 12:10
comments: true
categories: [Linux, nginx, CentOS, docker, Ansible, Container]
---
![](https://upload.wikimedia.org/wikipedia/commons/7/70/Orlando_Furioso_10.jpg)

Ansible distribution contains a whole slew of modules for doing many different things out-of-box. Besides other useful items, Ansible has on board a module named **docker**. As its name implies, it knowns how to install software in a form of Docker containers. But, in order to get actual work done, it relies on Docker package installed on a server. In these article, we'll install and tune Docker daemon using Ansible and prove it works by deploying a pair of simple containers on it.

I've choose to get Docker from the vendor's site, simply because the task of installing it using RPMs from CentOS Extras repository looks too trivial to describe. The first thing to do is to create the configuration file for Docker YUM repository:

```
# content of docker-main.repo.j2
[docker-main-repo]
name=Docker main Repository
baseurl=https://yum.dockerproject.org/repo/main/{{ ansible_distribution.lower() }}/{{ ansible_distribution_major_version }}
enabled=1
gpgcheck=1
gpgkey=https://yum.dockerproject.org/gpg
```

And then install it:

```
  - name: Install docker repository
    template:
      src: docker-main.repo.j2
      dest: /etc/yum.repos.d/docker-main.repo
      owner: root
      mode: 0644
```

The RPM containing Docker itself is named **docker-engine**, but Ansible **docker** module has one more prerequisite: Docker Python API. So, let's install the both during one task:

```
  - name: Install docker
    yum:
      name: "{{ item }}"
    with_items:
    - docker-engine
    - docker-python
```

I've to admit here that Docker installed from the vendor repository is a bit strange beast. For example, it doesn't know how to read daemon's command-line options from any external file. So let's teach it how to look for them into a file named **/etc/sysconfig/docker** (according to the convention for daemons running on RedHat-based systems.) In order to do so, we've to change two lines in Docker service's configuration file:

```
- name: Patch Docker service definition
  hosts: docker
  vars:
    docker_service_file: /usr/lib/systemd/system/docker.service
    docker_daemon_opts:
    - "-H fd://"
    - "--selinux-enabled=false"
  tasks:
  - name: Add path to configuration file to docker service definition
    lineinfile:
      name: "{{ docker_service_file }}"
      line: 'EnvironmentFile=/etc/sysconfig/docker'
      insertafter: '^\[Service\]$'
    notify:
    - reload systemd
    - restart docker

  - name: Add options substitution to docker service definition
    replace:
      name: "{{ docker_service_file }}"
      regexp: '^(ExecStart=/usr/bin/docker daemon) .+$'
      replace: '\1 $OPTIONS'
    notify:
    - reload systemd
    - restart docker
```

And then create **/etc/sysconfig/docker** from this template:

```
# content of docker.j2
OPTIONS='{{ " ".join(docker_daemon_opts) }}'
```

And upload it to our server:

```
  - name: Create docker configuration
    template:
      src: docker.j2
      dest: /etc/sysconfig/docker
      owner: root
      mode: 0644
    notify:
    - restart docker
```

Note that the last three tasks modify Docker daemon's command-line, so this daemon should be restarted in case when something had be changed by them (the first two also change service description stored in Systemd configuration directory, so this daemon should also be reloaded.) It can be achieved by creating two handlers (the order here is important - it's exactly the order in which they would be run in case a change happens):

```
  handlers:
  - name: reload systemd
    shell: systemctl daemon-reload

  - name: restart docker
    service:
      name: docker
      state: restarted
```

By now, that we've patched our Docker service, it can be started. If you're going to use Docker local UNIX socket (Docker does so by default) don't forget to create this socket first:

```
  - name: Start docker services
    service:
      name: "{{ item }}"
      enabled: yes
      state: running
    with_items:
    - docker.socket
    - docker
```

In case you have intention to work with Docker containers on behalf of a non-privileged user, this user must be added to **docker** group. For example:

```
  - name: Add vagrant user to docker group
    user:
      name: vagrant
      groups: docker
      append: yes
```

Now, that our Docker is daemon installed and running, we can start working with containers. First, let's create a simple **busybox** container exporting local `/var/www/html` directory (where all the files we're going to share with the world will be stored) as a Docker volume:

```
  - name: Create web data container
    docker:
      name: webdata
      image: busybox
      state: present
      volumes:
      - /distr/www:/usr/share/nginx/html:ro
```

Note that this container haven't to be started in order to fullfill its purpose.

The next container we're going to create is a bit more interesting. It's an official **nginx** container which will:

1. export its 80 port as 8080 port on the server where Docker daemon is running

2. automatically restart after the server's reboot

3. use the data volume exported by **webdata** container

```
  - name: Start nginx container
    docker:
      name: webserver
      image: nginx
      ports:
      - "8080:80"
      restart_policy: always
      volumes_from:
      - webdata
```

At this point, it should be possible to open a web browser, connect to 8080 port of our server and see the content we've put in `/var/www/html` directory.

Note that the way Ansible **docker** module behaves is somewhat differenet from one you've used to while working with another Ansible modules. The problem is that Docker can't change some container's parameters (such as **restart_policy** or **volumes_from**), if a container already exists. So, you couldn't do that by Ansible module too. For example, if you run the above task once, then change **restart_policy** parameter in it and run it again, the result will be quite unpredictable. If you wonder about a list of container parameters you can set only at creation time, compare documentation on `docker create` and `docker start` subcommands.

In case you're insterested in what's going in your container (or might be simply seek to examine, which configuration your nginx server has by default) and want to get into the **nginx** container, you should first learn its id:

```
webid=`docker ps | awk '/ webserver$/ {print $1}'`
```

And than exec a shell into it:

```
docker exec -it $webid /bin/bash -l
```

**-i** option here stands for 'interactive', **-t** - for 'TTY', and **-l** belongs to `/bin/bash` and stands for 'act as a login shell (i.e., read user profile on startup)'.

For the futher reading on Docker, consult with its [official documentation](https://docs.docker.com/), and the list of the possible arguments of Ansible **docker** task can be found [here](http://docs.ansible.com/ansible/docker_module.html).

All the files belonging to the playbook described above dwell at [my github page](https://github.com/alces/essentia-et-accidentia/tree/master/code-samples/depolyDockerByAnsible).

----

Tested against `Ansible 1.9.4`, `CentOS 7.1.1503`, and `Docker 1.9.1`