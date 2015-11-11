---
layout: post
title: 'Writing Init Script for Apache Tomcat'
date: 2015-11-09 15:01
comments: true
categories: [java, bash, Tomcat, init]
---
![](https://upload.wikimedia.org/wikipedia/commons/8/80/Lechatbotte1.jpg)

Apache Tomcat's binary distribution downloaded from [the vendor site](http://tomcat.apache.org/download-80.cgi) contains no SystemV-compatible init script, so let's create a custom one.

## 1. A quick-and-dirty solution

The **catalina.sh** script living in the **bin** directory of Tomcat's distribution knows what to do when receiving **start** and **stop** command, so the simplest possible script for starting and stopping Tomcat would be as short as that:

```
#!/bin/bash
owner=tomcat
export CATALINA_HOME=/opt/tomcat
su -s /bin/sh -c "$CATALINA_HOME/bin/catalina.sh $1" $owner
```

(here and bellow variables with the lowercase names are local variables of the script, those with uppercase names are global variables visible by a Tomcat process).

The **-s** argument of the **su** command is required only if your **tomcat** user have a pseudo-shell (such as `/sbin/nologin`) for its login shell. You also can use the following **sudo** command-line instead of **su**:

```
sudo -E -u $owner $CATALINA_HOME/bin/catalina.sh $1
```

The _"Capital E"_ argument tells **sudo** to preserve current environment (a thing **sudo** normally doesn't do for some security reasons).

## 2. Adding more feautures

If you run Tomcat on the one of the _Red Hat_ derivatives, you apparently would like to add compatibility with the **chkconfig** utility to your `/etc/init.d/tomcat`. In order to do so add the following "magic" comment line right after the _bang-line_:

```
# chkconfig: 345 97 03
```

(`235` is a list of runlevels you want Tomcat to start on, `97` and `03` determines an order your service would be started after system boot and stopped before shutdown)

It would be conveniently enough, if our script understanded not only **start** and **stop** command, but **restart** too. **catalina.sh** by inself knows nothing about how to do restart, so we should tell it and replace the simple last line of our script by a way more complicated construction:

```
catalina() {
        su -s /bin/sh -c "$CATALINA_HOME/bin/catalina.sh $1" $owner
}

case $1 in
restart)
        catalina stop
        catalina start
        ;;
*)
        catalina $1
        ;;
esac
```

A bad news is that over a half of web applications existing in our imperfect world don't like to stop when Tomcat tells them to do so. Its developers know about that and add a optional **-force** parameter to the **stop** command. So, if an application you deploy on your Tomcat is from those stubborn ones, you could bring it to heel by rewriting our **catalina** function the following way:

```
$timeout=15
catalina() {
        if [ "$1" = "stop" ]
        then
                cmd="stop $timeout -force"
        else
                cmd="$1"
        fi
        su -s /bin/sh -c "$CATALINA_HOME/bin/catalina.sh $cmd" $owner
}
```

Where **$timeout** is a number of seconds to wait between saying `catalina.sh stop` and sending a _SIGKILL_ to Tomcat process.

But in order to work that way our script have to know where Tomcat's PID-file is (surprisingly enough,there's no default location.) We can let him know by setting an environment variable named **CATALINA_PID**:

```
export CATALINA_PID=$CATALINA_HOME/logs/tomcat.pid
```

(PID-file isn't put into a conventional `/var/run` directory, because normally **tomcat** user has no write permissions there)

If you need reaction on the **status** command in your script, it'll be a tricky part, because **catalina.sh** has no subcommand telling something about a current status of Tomcat service. My personal (of course, imperfect) variant of **status** function can be seen bellow:

```
status() {
        if [ -f "$CATALINA_PID" ]
        then
                pid=`cat $CATALINA_PID`
                if [ "`ps ho user -p $pid`" = "$owner" ]
                then
                        echo "Running (PID: $pid)"
                        exit 0
                else
                        echo "PID-file exists, but process isn't found"
                fi
        else
                echo "Stopped"
        fi
        exit 1
}
```


## 3. Changing JVM behaviour

If you've downloaded from [Oracle Java Archive](http://www.oracle.com/technetwork/java/javase/archive-139210.html) (or somewhere else) some historic version of JVM and want to run Tomcat by it, because let's say some old buggy clients works well only against this particular JVM, you can tell Tomcat where the desirible Java lives by setting **JAVA_HOME** variable in the preamble of your script. For example:

```
export JAVA_HOME=/opt/jdk1.7.0_45
```

Also, oftentimes happens that the default values of JVM heap and permanent memory values aren't sufficient for your web applications' needs. You can change them by setting **CATALINA_OPTS** varible:

```
export CATALINA_OPTS="-server -Xms1024m -Xmx1024m -XX:PermSize=512m -XX:MaxPermSize=512m"
```

(setting start memory values equal to the maximal ones is highly recomandend for any server JVM processes)

If you set a well-known **JAVA_OPTS** instead of **CATALINA_OPTS**, it'll work too, but variant with **CATALINA_OPTS** is recomended by comments inside **catalina.sh** script, because it affects only the process of Tomcat itself and doesn't affect short-lived processes starting and stopping your Tomcat instance.

## 4. Separation between program and data

By default, Tomcat stores all its files: scripts, libraries, configs, logs, and deployed web applications in one file hierarchy, but I think, it's not such a bad idea to separate our binary installation from changeable files (after all, that's one of the key ideas under the standard Unix file layout.) With a such kind of setup it's possible to run multiple Tomcat instances using the exactly same version of binaries, but operating on the different ports and with the different set of web apllications deloyed upon them, or even more important, to upgrade (or maybe downgrade) your Tomcat installation without affecting its configurations files and webapps' directories.

Before telling Tomcat, where a new home for its moving parts is, we have to create a set of necessary directories (let's name its root `/opt/MyWebApp`), give away them to Tomcat's owner, and copy the serever's configuration:

```
mkdir -p /opt/MyWebApp/{logs,temp,work}
chown tomcat:tomcat /opt/MyWebApp/{logs,temp,work}
rsync -a /opt/tomcat/{conf,webapps} /opt/MyWebApp/
```

Then, in order to let Tomcat know about our new setup, an environment variable named **CATALINA_BASE** should be defined in the preamble of our init script (by default, it set equal to the **CATALINA_HOME**):

```
export CATALINA_BASE=/opt/MyWebApp
```

And the last thing I want to let you know for today. A full-blown init script for Tomcat containing all the aforementioned features can be found at [my page on github](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/tomcat_init_script.sh).

---

Tested against `apache-tomcat-8.0.28`, `CentOS 6.4`