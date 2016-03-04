---
layout: post
title: 'Teaching Apache Tomcat How to Live Inside a Container'
date: 2016-02-03 18:39
comments: true
categories: 
---
![](http://uploads8.wikiart.org/images/gustave-dore/don-quixote-61.jpg!HalfHD.jpg)

Tecnically speaking, each and every Linux application can be started inside a Docker container - the only strict requirement is that your application must know how to run in foreground (otherwise, a container simply would finish its live momentarily.) But, according to the manifest named [The Twelve-Factor App](http://12factor.net/), there're some best practices an application one could call a good container citizen must follow. Oftentimes, it isn't possible to make a venerable application conform with all these best practices, but at least something could be done. In this article, I'm going to share with you some modification which can be applied to Apache Tomcat distribution in order to make it a bit more container-friendly. I think, this particular application is espesially interesting, because many enterprise application having web interface (such as [Artifactory](https://www.jfrog.com/open-source/) or [Sonarqube](http://www.sonarqube.org/)) use it under the hood; so, the similar approches could be used in order to containeraze them.

I believe, everyone who has ever tryed to put something nontrivial in a Docker container, quickly realize that the set of instruction one can put into a **Dockerfile** isn't enough, because:

1. the language **Dockerfile**s are intended to be written in is pretty primitive (quite may be, _intentionally_ primitive)

2. every usage of **RUN** directive creates a new level in container's filesystem, so, if you were to do something interesting in your **Dockerfile**, you would got a container having many unnecessary filesystem's layers as the result.

Certainly, the both issues can be addressed by writing some sort of bash (or might be even Python) script performing all the manipulations with the distribution before putting it inside container, but,  personally, I think that Ansible playbooks are a lot more readable than any kind of scripts, so let me again describe our workflow by Ansible tasks.

Here's a list of the variables I'm going to use in the following tasks (of course, you can change some of these values according to your preferences):

```
build_dir: /var/stage/tomcat
remove_access_logs: no
tmp_dir: /var/tmp
tomcat_version: 8.0.30
tomcat_owner: tomcat
tomcat_target_dir: /opt/tomcat
tomcat_dir_name: "apache-tomcat-{{ tomcat_version }}"
tomcat_arc_name: "{{ tomcat_dir_name }}.tar.gz"
tomcat_major_version: "{{ tomcat_version.split('.')[0] }}"
tomcat_stage_dir: "{{ build_dir }}/{{ tomcat_dir_name }}"
tomcat_server_xml: "{{ tomcat_stage_dir }}/conf/server.xml"
```

The first thing we have to do in order to do build Docker containers is to install Docker and run Docker daemon (note that this time we'll use Ubuntu, so the name of Docker package will be **docker.io**):

```
  - name: Install docker
    apt:
      name: docker.io
      update_cache: yes

  - name: Start docker service
    service:
      name: docker
      state: running
```

The next step is to download Tomcat binary disribution from the vendor's site and to unpack it into a stage directory:

```
  - name: Download Tomcat archive
    get_url:
      url: "http://archive.apache.org/dist/tomcat/tomcat-{{ tomcat_major_version }}/v{{ tomcat_version }}/bin/apache-tomcat-{{ tomcat_version }}.tar.gz"
      dest: "{{ tmp_dir }}"

  - name: Create build directory
    file:
      name: "{{ build_dir }}"
      state: directory

  - name: Unpack Tomcat distributuion
    unarchive:
      src: "{{ tmp_dir }}/{{ tomcat_arc_name }}"
      dest: "{{ build_dir }}"
      copy: no
      creates: "{{ tomcat_stage_dir }}"
```

Everyone who is familiar with Tomcat knows that its **bin** subdirectory contains script for both the Unix-like systems and Windows. The former have `*.sh` suffix, the latter - `*.bat`. The presense of the windows scripts makes no sense in our Docker container, so let's create its list and delete them all:

```
  - name: Find .bat files inside bin subdirectory
    find:
      paths: "{{ tomcat_stage_dir }}/bin"
      patterns: "*.bat"
    register: tomcat_bat_files

  - name: And delete them
    file:
      name: "{{ item.path }}"
      state: absent
    with_items: tomcat_bat_files.files
```

Note that **find** task wasn't supported by Ansible before version 2.0, so, why don't upgrade right now?

The thing we're going to teach our Tomcat is writing all the log messages to stdout instead of the conventional files. The minimal viable **logging.properties** file doing just that is the following:

```handlers = java.util.logging.ConsoleHandler
.handlers = java.util.logging.ConsoleHandler

java.util.logging.ConsoleHandler.level = FINE
java.util.logging.ConsoleHandler.formatter = org.apache.juli.OneLineFormatter

org.apache.catalina.core.ContainerBase.[Catalina].[localhost].level = INFO
org.apache.catalina.core.ContainerBase.[Catalina].[localhost].handlers = java.util.logging.ConsoleHandler

org.apache.catalina.core.ContainerBase.[Catalina].[localhost].[/manager].level = INFO
org.apache.catalina.core.ContainerBase.[Catalina].[localhost].[/manager].handlers = java.util.logging.ConsoleHandler

org.apache.catalina.core.ContainerBase.[Catalina].[localhost].[/host-manager].level = INFO
org.apache.catalina.core.ContainerBase.[Catalina].[localhost].[/host-manager].handlers = java.util.logging.ConsoleHandler
```

So, let's put it in to the proper place:

```
  - name: Replace logging.properties for writing everything to stdout
    copy:
      src: logging.properties
      dest: "{{ tomcat_stage_dir }}/conf"
```

Unfortunally, in order to achieve our goal, it isn't enough. The bad news is that Tomcat has one more class of logs knowing nothing about the settings one could make through **logging.properties** - it's the access log configuring through **server.xml**. The even worse news is that currently there's no way to say Tomcat to put this log to stdout. So, we've a choice. May be you aren't interested in Tomcat access log recoreds at all (for example, you can control access to your server using a HTTP proxy server logs.) If so, you can simply comment-out in **server.xml** XML block adding **AccessLogValve**, for example, that way:

```
- name: Open a comment around access log valve
  replace:
    name: "{{ tomcat_server_xml }}"
    regexp: '^(\s+)(<Valve className="org\.apache\.catalina\.valves\.AccessLogValve".*)$'
    replace: '\1<!-- \2'
  when: remove_access_logs

- name: And close this comment
  replace:
    name: "{{ tomcat_server_xml }}"
    regexp: '^(\s+pattern="%h %l %u %t &quot;%r&quot; %s %b" />[\t ]*)$'
    replace: '\1 -->
  when: remove_access_logs
```

Then, you may want to remove **logs** subdirectory, because for now we've nothing to write into it:

```
- name: Remove logs subdirectory
  file:
    name: "{{ tomcat_stage_dir }}/logs"
    state: absent
  when: remove_access_logs  
```

In case you don't want remove Tomcat access logs, you can get them out of Docker union filesystem by putting them into volume (the actual way to do that see in **Dockerfile** template below.) If you've choose this variant, you likely want to purge them periodically. For example, to remove those older than a week by a following bash script (I believe, you should put running something like that in your crontab):

```
for cntr in $(docker ps | awk '{if ($2 ~ /^tomcat/) {print $1}}')
do
	docker run --volumes-from=$cntr busybox find /opt/tomcat/logs -type f -mtime +7 -delete
done
```

Here we're parsing output of `docker ps` in order to get only a list of Tomcat container's id from it and run a **busybox** container sharing their `/opt/tomcat/logs` volume. This - truly ephemeral - container lives only while **find** command inside it searching for too old log files and removing them. Alternatively, one could run **find** without creating a new container by employing `docker exec` instead of `docker run`:

```
	docker exec $cntr find /opt/tomcat/logs -type f -mtime +7 -delete
```

For now, we're ready to create a **Dockerfile**. Our template for it is the following:

```
FROM java:8-jre
MAINTAINER Alexey Zalesnyi

COPY {{ tomcat_dir_name }} {{ tomcat_target_dir }}
{% if tomcat_owner != 'root' %}
RUN useradd {{  tomcat_owner }} && chown -R {{ tomcat_owner }}:{{ tomcat_owner }} {{ tomcat_target_dir }}
{% endif %}
EXPOSE 8080 8443
{% if not remove_access_logs %}
VOLUME ["/opt/tomcat/logs"]
{% endif %}
{% if tomcat_owner == 'root' %}
CMD ["{{ tomcat_target_dir }}/bin/catalina.sh", "run"]
{% else %}
CMD ["su", "-c", "{{ tomcat_target_dir }}/bin/catalina.sh run", "{{ tomcat_owner }}"]
{% endif %}
```

The most interesting part here is changing ownership of Tomcat directory and process in case when **tomcat_owner** variable set to something other than **root** (by default, a process started by Dockerfile **CMD** runs on behalf of superuser.)

The tasks for making final **Dockerfile** from this template and building an image from it are the following:

```
  - name: Create Dockerfile
    template:
      src: Dockerfile.j2
      dest: "{{ build_dir }}/Dockerfile"

  - name: Build Docker image
    shell: "docker build -t tomcat {{ build_dir }}"
```

Note that I've put a raw `docker build` command instead of using Docker own **docker_image** module. I've done so, because in my case **docker_image** seems broken. [They said](https://github.com/ansible/ansible-modules-core/issues/2962#issuecomment-180433421), the real source of the issue is incompatibility between my version of Ansible and **python-docker** from the Ubuntu Vivid repository, but, anyway, the `docker build` is somewhat idempotent by itself: if the desired image already exists, it really will do nothing and return very quickly.

So, for now it's possible to put a WAR file of your favorite application into `/var/webapps` directory and start our newborn **tomcat** container with this directory mounted:

```
docker run -d -p 8080:8080 -v /var/webapps:/opt/tomcat/webapps tomcat
```

[Here](https://github.com/docker-library/tomcat/) one can find **Dockerfile**s for the official Docker image for Apache Tomcat, which can be started simply by putting a name **tomcat** in the command line of `docker run` command. Personally, I don't agree with all the techniques used by their creators, but - as an official source - they at least worth a glance.

The full source of all the files mentioned in the arcicle can be found at [my github page](https://github.com/alces/essentia-et-accidentia/tree/master/code-samples/buildDockerImage4Tomcat).

------

Tested against `Ansible 2.0.0.2`, `Apache Tomcat 8.0.30`, `Ubuntu 15.04`