---
layout: post
title: 'Nginx Installation and Configuration via Ansible Playbook'
date: 2015-12-25 12:30
comments: true
categories: [nginx, Https, OpenSSL, proxy, Ansible]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-99.jpg)

Ansible playbooks, so to speak, happily occupy middle ground between administrative scripts and installation guides. You can use them for both:

* actually doing things and

* describing how things must be done in a form quite readable for techically literate human beings.

In this article I'm going to codify as Ansible playbook a small piece of the tribal knowledge: how to install the last version of nginx from the vendor's repository on RHEL 6/7 and configure it in order to act as a reverse proxy on the privileged TCP ports and an HTTPS terminator for a Java web application running behind it on behalf of a non-privileged user.

As you might be aware, the problem is that:

1. it's generally not recommended to run daemon processes on behalf of **root** for security reasons, and

2. in Unix-like systems, a process started on behalf of a non-privileged user can't listen on the ports with numbers below 1024, but

3. JVM doesn't know how to make **setuid** system call (i.e., to start working on behalf of **root**, occupy a privileged port, then change an effective user id to a non-privileged one, as every Unix daemon written in C - or any other language having access to native system libraries: Perl, Python, Ruby, etc. - normally does.)

The shortest way out of this problem is to divert TCP packets from the standard HTTP and/or HTTPS ports to the ports with numbers greater than 1024 by **iptables** (or maybe other basic firewall software such as **ipfw** in case of using FreeBSD.) If you're interested in this kind of setup, look for instructions [here](https://wiki.debian.org/Firewalls-local-port-redirection). But using reverse proxy seems to be a more flexible approach. For example, if you were to run multiple Java web applications on the same server (maybe, Jenkins on its default 8080 port and Nexus on the 8081 - why not?), it wouldn't be possible to process requests for the both on the same port, because your firewall knows nothing about URL rewriting. Also, if using HTTPS was required in your enviroment, it would be a lot simpler (and would work a bit faster) to configure encrypted connection once - on the proxy server - and send traffic between it and Java applications through plain old HTTP (note that this kind of configuration inflicts no damage to your enviroment's security, because communication between proxy server and application's endpoint occurs on loopback interface.)

The playbook we're going to write in this article presumes that there's a some sort of Ansible inventory (in the simplest possible case, an INI file or maybe a some kind of the [executable one](http://essentia-et-accidentia.logdown.com/posts/316980)) and there's a group named **proxy** defined in it (this group should consist of all the servers we're planning to install nginx on.) Also, we need a superuser access to these servers configured under `[defaults]` section of `ansible.cfg` in one of the possible forms:

* as root using password:

```
ask_pass: true
remote_user: root
```

* as root using key pair:

```
private_key: nginx_id_rsa
remote_user: root
```

* using key pair and passwordless sudo:

```
private_key: nginx_id_rsa
remote_user: vagrant
sudo: true
```

* using sudo with password:

```
ask_pass: true
ask_sudo_pass: true
remote_user: vagrant
sudo: true
```

When writing Ansible playbooks, I prefer to make a palybook file executable and add the following 'shebang' line atop it:

```
/usr/bin/env ansible-playbook
```

After this simple tuning, my playbook can be executed as any other script in Unix-like environment - simply by its filename. I use `/usr/bin/env` in order to be able to run the playbook by Ansible installed into any directory mentioned in **PATH** (`/usr/bin`, `/usr/local/bin`, etc.)

The playbook consists of the only one play, and its preabmle is quite simple:

```
---
- name: Install nginx and configure it as reverse proxy & HTTPS terminator
  hosts: proxy
  vars_files:
  - params.yml
```

In order to don't touch the whole playbook when we want to change one of its parameters, I've put the variables you likely might want to change into a separate file named `params.yml`. The variables' names talks for themselves:

```
nginx_mainline: true
nginx_proxy_port: 8080
```

The values shown above mean that we're going to install nginx from mainline branch (instead of the stable one) and forward HTTP requests to 8080 port (the standard one for Jetty and Tomcat.) The actual business of our play is described by the tasks it contains. As the first step, we want to configure nginx repository for **yum** packet manager:

```
  tasks:
  - name: Install nginx repository
    template:
      src: nginx.repo.j2
      dest: /etc/yum.repos.d/nginx.repo
      mode: 0644
```

As you might notice, I use the YAML-like syntax for setting task's arguments instead of the more concise standard one, because this approarch to syntax seems to be more holistic. If you ought to comply with the coding style of official Ansible documentation - or maybe just like to use short forms everywhere you could - it'll be possible to write the same task that way:

```
  tasks:
  - name: Install nginx repository
    template: src=nginx.repo.j2 dest=/etc/yum.repos.d/nginx.repo mode=0644
```

During this task we're creating from a [Jinja2 template](http://jinja.pocoo.org/docs/dev/templates/) configuration file for nginx yum repository and installing it on the target server(s). The template looks like that:

```
[nginx]
name=nginx repo
baseurl=http://nginx.org/packages/{{ 'mainline/' if nginx_mainline else '' }}rhel/{{ ansible_distribution_major_version }}/{{ ansible_machine }}/
gpgcheck=0
enabled=1
```

Note Jinja2 syntax for **inline if** - it's similar to the ternary operator in Python. `ansible_distribution_major_version` and `ansible_machine` mentioned here are the standard Ansible facts. If you wonder which facts are available on your server (and which values they're currently set to,) look at an output of the following command (the output can be quite long, so you may want to somehow grep it):

```
ansible your.host.name -m setup
```

Our next task installs nginx itself and is as simple as that:

```
  - name: Install nginx rpm
    yum:
      name: nginx
```

In order to serve HTTPS requests, our nginx has to have SSL certificate and SSL key, and by default it has none. Also be aware that, unlike Apache HTTPD, nginx only knows how to operate using passwordless keys. If you're planning to use a self-signed certificate, you can generate one (valid for 1000 days) along with a key of the required type by the following command:

```
openssl req -new -x509 -nodes -out server.crt -keyout server.key -days 1000
```

Generally speaking, self-singed certificates must not be used in the real production environments, although I came across such sort of setup sometimes in the past.

In case you already have an SSL key protected by password, it can be made passwordless by running:

```
openssl rsa -in protected.key -out passwordless.key
```

(you'll be asked for the key's pass phrase)

When we have a certificate and its key, it'll be pretty easy to tell Ansible to install them into an appropriate place:

```
  - name: Install SSL certificate and key
    copy:
      src: "server.{{ item }}"
      dest: "/etc/nginx/server.{{ item }}"
      mode: 0600
    with_items:
    - crt
    - key
```

The most interesting part of working with Unix deamons is writing their config. A template we can generate minimal working nginx config from is here:

```
events {
	worker_connections 1024;
}
http {
	server {
		listen 80;
		server_name {{ ansible_nodename }};
		return 301 https://$server_name$request_uri;
	}
	server {
		listen 443 ssl;
		server_name {{ ansible_nodename }};
		ssl_certificate /etc/nginx/server.crt;
		ssl_certificate_key /etc/nginx/server.key;
		location / {
			proxy_pass http://127.0.0.1:{{ nginx_proxy_port }};
		}
	}
}
```

As you might have already guessed, `ansible_nodename` is Ansible fact containing the full DNS name of the target host. The line starting with the word **return** redirects requests from unencrypted HTTP port to HTTPS. In order to install nginx config into its place, let's write our next task:

```
  - name: Create nginx configuration
    template:
      src: nginx.conf.j2
      dest: /etc/nginx/nginx.conf
      mode: 0644
    notify: restart nginx
```

After making changes to its configuration, nginx must be reloaded, so we add `notify` clause to the task. The argument of this clause is the name of the handler, and in order to make this task work, we should add definition of this handler below the last task in the play:

```
  handlers:
  - name: restart nginx
    service:
      name: nginx
      state: restarted
```

Ansible runs all the handlers from a play after the last its task in the sequence they was mentioned in playbook, but only in the case when the tasks they're bound to has changed something (to be presice, returned the **changed** status) during the current run of the playbook.

The last thing we want to do is actually start nginx service and make it start automatically at boot time:

```
  - name: Start nginx service
    service:
      name: nginx
      enabled: yes
      state: started
```

If after running this playbook everything seems running, but you can't see anything in the browser window, first check whether TCP ports 80 and 443 are open in your firewall settings. In case you want to turn off firewall altogether - and it might be a quite reasonable sort of setup for servers available only from the internal network(s) - it can be achieved by the following Ansible task:

```
  - name: Disable firewall
    service:
      name: "{{ 'firewalld' if ansible_distribution_major_version == '7' else 'iptables' }}"
      enabled: no
      state: stopped
```

All the files belonging to the playbook described above can be found on [my github page](https://github.com/alces/essentia-et-accidentia/tree/master/code-samples/nginxReverseProxy).

-------

Tested against `Ansible 1.9.4`, `CentOS 7.1.1503`, `OEL 6.4`, `nginx 1.9.9`