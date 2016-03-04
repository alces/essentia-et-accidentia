---
layout: post
title: 'Turning TeamCity into a Linux Service'
date: 2016-02-01 10:08
comments: true
categories: [bash, Systemd, Ansible, TeamCity]
---
![](http://uploads0.wikiart.org/images/gustave-dore/don-quixote-136.jpg!HalfHD.jpg)

[TeamCity](https://www.jetbrains.com/teamcity/) server distribution doesn't contain either SystemV-style init script, or Systemd service file (only Windows service definition is there.) In this article, we're going to overcome such an iniquity and create viable examples of the both. In order to don't repeat some environment-specific constants again and again, but - according to the industry best practices - define them all in one place, the tagret files will be created from Jinja2 templates and put into their places by a (very simplistic) Ansible playbook.

The list of variables we're going to use during the process are the following (you might want to change some of their values according to the specifics of your environment):

```
  vars:
    services_dir: /usr/lib/systemd/system
    teamcity_env_file: /etc/sysconfig/teamcity
    teamcity_install_prefix: /opt
    teamcity_memory_opts: "-Xms1024m -Xmx1024m -XX:PermSize=512m -XX:MaxPermSize=512m"
    teamcity_owner: teamcity
    teamcity_group: teamcity
    teamcity_home: "/home/{{ teamcity_owner }}"
    teamcity_root_dir: "{{ teamcity_install_prefix }}/TeamCity"
    teamcity_data_dir: "{{ teamcity_root_dir }}/data"
    teamcity_init_script: "{{ teamcity_root_dir }}/bin/teamcity-server.sh"
    teamcity_timeout: 30    
```

The first thing we want to create is the file containing environment variables for TeamCity. In order to be compliant with the RedHat traditions, we'll call it `/etc/sysconfig/teamcity`:

```
  - name: Create TeamCity environment file
    template:
      src: teamcity-env.j2
      dest: "{{ teamcity_env_file }}"
    notify:
    - restart teamcity
```

Our sample template for this file contains two variables: the path to TeamCity data directory and JVM memory options using by TeamCity server process:

```
TEAMCITY_DATA_PATH={{ teamcity_data_dir }}
TEAMCITY_SERVER_MEM_OPTS={{ teamcity_memory_opts }}
```

The names of the other enviroment variables supported by TeamCity and their meanings can be found in the comment atop `{{ teamcity_root_dir }}/bin/teamcity-server.sh` script. Note that TeamCity server should be restarted in the event this file has been changed. The actual definition of the `restart teamcity` notifier (as well as the other notifiers we're going to mention during the course of the article) will be put below, because - according to the syntax of Ansible playbooks - all the notifiers using during a play should be placed after all its tasks.

The next thing to do is to create a plain old SystemV-style init script for running TeamCity server under RHEL version 6 and below:

```
  - name: Create TeamCity init script
    template:
      src: teamcity-init.j2
      dest: /etc/init.d/teamcity
      mode: 0755
    when: ansible_distribution_major_version < '7'
    notify:
    - restart teamcity
```

`teamcity-init.j2` template itself is quite long, so we're going to discuss it step by step. It starts with two convetional lines of commentary: the so-called 'shebang', and the line used by **chkconfig** utility to learn on which runlevels (and in which order) this service will be started and stoped:

```
#!/bin/bash
# chkconfig: 2345 95 55
```

The next step is to read environment variables from `{{ teamcity_env_file }}`. The first command in the snippet automatically exports into environment all the variable we'll define below it:

```
set -a
. {{ teamcity_env_file }}
```

In the case when the variable named **TEAMCITY_PID_FILE_PATH** hasn't been mentioned in the environment file, its default value should be set (because this value will be used for determining the service's status):

```
if [ -z "$TEAMCITY_PID_FILE_PATH" ]
then
    TEAMCITY_PID_FILE_PATH={{ teamcity_root_dir }}/logs/teamcity.pid
fi
```

Note that we don't have to explicitly export this variable due to `set -a` command we've put above.

The next snippet goes into TeamCity home directory (because TeamCity will fail to start if it run from a directory where its user has no permissions to write to) and then starts TeamCity server using its own script:

```
start() {
    cd {{ teamcity_home }}
    sudo -E -u {{ teamcity_owner }} {{ teamcity_init_script }} start
}
```

Note **-E** key of **sudo** command which tells sudo to preseve current environment variables (as you might know, the contemporary versions of this tool don't normally do for security reasons.)

The next function stops TeamCity server. It uses Apache Tomcat under the hood, so the only working variant of **stop** subcommand is the forced one:

```
stop() {
    sudo -E -u {{ teamcity_owner }} {{ teamcity_init_script }} stop {{ teamcity_timeout }} -force
}
```

If you looked into **teamcity-server.sh** script, you would notice that it simply passes its arguments to **catalina.sh**, so an every argument known by the latter would work well with the former.

I won't describe the `status()` function here, because its code is exactly the same as the one of that I already described in [the article on writing init script for Tomcat](http://essentia-et-accidentia.logdown.com/posts/310803). The only real difference is using **TEAMCITY_PID_FILE_PATH** environment variable instead of **CATALINA_PID**.

So, the final part of the init script is parsing of the command line and running the desired function from those we've already defined above:

```
case $1 in
start)
    start
    ;;
stop)
    stop
    ;;
restart)
    stop
    start
    ;;
status)
    status
    ;;
*)
    echo "Usage $0 {start|stop|restart|status}"
    exit 2
    ;;
esac
```

For now, we're going to create TeamCity service definition for Systemd. Technically speaking, this step isn't necessary, because the old school init scripts are still supported in the RHEL-7 systems. But, personally, I'm not such a big fun of shell scripts and 7-levels init to still use it since a new generation of tools has been already introduced (and is already widely used.) Ansible task for create the service file for Systemd is the following:

```
  - name: Create TeamCity service file
    template:
      src: teamcity.service.j2
      dest: "{{ services_dir }}/teamcity.service"
    when: ansible_distribution_major_version >= '7'
    notify:
    - reload systemd
    - restart teamcity
```

Note that in the event of changing service's definition, we should not only restart TeamCity itself, but also tell Systemd to reload its services' base. It's, of course, is an additional complexity, but it's payd for by the fact that the template for the service file itself this time is a lot simpler:

```
[Unit]
Description=TeamCity CI server
After=network.target

[Service]
Type=forking
User={{ teamcity_owner }}
Group={{ teamcity_group }}
EnvironmentFile={{ teamcity_env_file }}
ExecStart={{ teamcity_init_script }} start
ExecStop={{ teamcity_init_script }} stop {{ teamcity_timeout }} -force
TimeoutSec={{ teamcity_timeout }}

[Install]
WantedBy=multi-user.target
```

And that's all. We only have to tell Systemd some basic facts about the service: generally, how to start it, how to stop it, and which user to choose in order to operate on behalf of - and we haven't bother about such a fuss as learning service's status, restarting, etc.

As long as we have all the service files for the both families of RHEL systems on place, we can now run our service and make it start at boottime:

```
  - name: Start TeamCity service
    service:
      name: teamcity
      enabled: true
      state: running
```

And here're the definitions of two handlers, I've mentioned during the course of the playbook:

```
  handlers:
  - name: reload systemd
    shell: systemctl daemon-reload

  - name: restart teamcity
    service:
      name: teamcity
      state: restarted
```

The full sources of all the files described above can be found at [my github page](https://github.com/alces/essentia-et-accidentia/tree/master/code-samples/teamCityService).

------

Tested against `ansible 1.9.4`, `CentOS 6.4`, `CentOS 7.1.1503`, `TeamCity Professional 9.1.6`