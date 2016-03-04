---
layout: post
title: 'Using Docker Provisioner in Vagrant'
date: 2016-01-20 14:49
comments: true
categories: [ubuntu, Linux, nginx, vagrant, CentOS, docker, Container]
---
![](https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Gustave_Dor%C3%A9_-_The_Monkey_and_the_Dolphin.jpg/606px-Gustave_Dor%C3%A9_-_The_Monkey_and_the_Dolphin.jpg)

If you use [Vagrant](https://www.vagrantup.com) in your daily work as hard as I do, you're likelly familiar with bringing its virtual machines to a desired state (or 'provisioning' if you'd like to put it in Vangant's jargon) by such tools as Chef and Puppet, which are supported by Vagrant for a long time. But things change, and the world become aware of new ways of installing software. The one that has turned into almost a buzzword in the last year or two is employing Docker containers, and Vagrant since version 1.4.0 has provisioner named **docker**. This provisioner is a quite smart guy indeed. It knows how to pull Docker images, build your own images upon them, run Docker containers, and even how to install Docker package shipped with a given Linux distribution (at the time of writing, this package has version 1.9.1 in case of Ubuntu and 1.8.2 for CentOS.) So, by employing it, you could perform almost all you might want to do with Docker containers directly from **Vagrantfile** without a need of tinkering with a general-pupose configuration management tool, such as Ansible, Chef, or Puppet.

So, a simple **Vagrantfile** creating an Ubuntu virtual machine running the official **nginx** container would be the following:

```
Vagrant.configure('2') do |config|
        config.vm.hostname = 'docker-test.example.com'
        config.vm.box = 'ubuntu/vivid64'
        config.vm.network :private_network, ip: '192.16.1.2'
        config.vm.provision :docker do |dock|
                dock.run 'webserver', image: 'nginx'
        end
end
```

Having this staff in place, you can run `vagrant up` from the directory your **Vagrantfile** is saved in, wait a couple of minutes (for the first time a bit longer indeed,) and you've got here: a machine with nginx installed is up and running.

The problem is such an isolated nginx installation is quite meaningless, because we haven't shared any of the container's ports into any public or private network yet. The second level problem is that, at the time of writing - accorging to its [official documentation](https://www.vagrantup.com/docs/provisioning/docker.html) - **run** method of **docker** provisioner has only six options (compare this number with the [about 60 command-line keys](https://docs.docker.com/engine/reference/commandline/run/) of the contemporary version of `docker run` subcommand) - and there's no option for sharing container's ports in this list. Luckily, our provisioner has a convenient **args** option capable of passing any keys, which aren't directly supported by our provisioner, to `docker run` subcommand. So, we can export 80 port of the container, which nginx listen on by default, as the 8080 port of the virtual machine by adding the following option to the row starting with `dock.run` (don't forget to add a comma after its previous option):

```
args: '-p "8080:80"'
```

Just for the sake of the demonstration, let's teach our nginx how to listen not only on 80 port, but on SSL 443 port too. The standard **nginx** container already expands 443 port, but nobody listens on it, because it makes no sense to answer on HTTPS requests while having no SSL certificates. Let's put our certificate (named **server.crt**), its key (named **server.key**), and the following configuration file (its name can be whatever you want, but its suffix should be **.conf**, so **ssl.conf**, or **my.conf**, or ever **1.conf** would be just fine) into a directory named `/var/www/conf.d` on the machine we run Vagrant on:

```
server {
	listen 80;
	listen 443 ssl;
	server_name localhost;

	ssl_certificate /etc/nginx/conf.d/server.crt;
	ssl_certificate_key /etc/nginx/conf.d/server.key;

	location / {
		root   /usr/share/nginx/html;
		index  index.html index.htm;
	}
}
```

Then we should make this directory shared with the virtual machine by adding the following line inside `Vagrant.configure('2')` block in our **Vagrantfile**:

```
config.vm.synced_folder '/var/www/conf.d', '/conf.d'
```

And the final step is to mount the virtual machine's directory on container as `/etc/nginx/conf.d`:

```
args: '-p "8080:80" -p "8443:443" -v /conf.d:/etc/nginx/conf.d:ro'
```

The **Vagrantfile** line with such a long option in it could become almost unreadable, so on the sake of legibility, you can put **args** option (as any other option of any other command) into a separate line, and Vagrant understands such kind of syntax - just don't forget that the previous line must be ended by comma. If you think that this syntax is too ambiguous, you can put all the argumetns of **run** method into brackets. The language **Vagrantfile** is written in is the plain old Ruby, so the brackets around the method's arguments are completely optional. String 'plus' operator and variables' substitution in the double-quoted strings using standard Ruby `#{varName}` syntax - if you were to use them - also would work as expected.

Now, in order to let Vagrant know that provisioner's configuration has changed, run `vagrant provisioner` (because Vagrant will never re-provisioner a already provisioned virtual machine, if you don't tell him to do so.) As you might already know, Docker doesn't support adding ports and volumes to an existing container, but our provisioner is clever enough to delete and re-create the container in order to apply this change.

The one major thing **docker** provision can't do is changing command-line options of Docker daemon process, and this is bluntly admitted in [its official documentation](https://www.vagrantup.com/docs/provisioning/docker.html). A possible workaround is proposed in the same document. The idea is to configure this file by some another provisioner placed in the **Vagrantfile** before our **docker** provisioner. It works, because:

1. Vagrant runs provisioners exactly in the order they have been mentioned in **Vagrantfile**,

2. **apt-get** and **yum** are polite enough to don't overwrite configuration files already existing on a host at the moment of installation by the default ones.

So, let's add the following line before the line starting with `config.vm.provision :docker` into our **Vagrantfile**:

```
config.vm.provision :shell, path: 'set-docker-opts.sh'
```

And put the file named **set-docker-opts.sh** in the directory **Vagrantfile** is saved in with the following content:

```
opts="--selinux-enabled=false"

if [ -f /etc/redhat-release ]
then
        echo "OPTIONS='$opts'" > /etc/sysconfig/docker
else
        if [ -f /etc/debian_version ]
        then
                echo "DOCKER_OPTS='$opts'" > /etc/default/docker
        else
                echo "Unsupported Linux distribution"
                exit 1
        fi
fi
```

The idea here is to guess, whether we're running on a RedHat-like or a Debian-like system and create a daemon configuration file for Docker having format understandable on this platform. Note that we don't have to make this file executable or add a so-called 'shebang' as its first line, because we've explicitly told Vagrant that it's a script to run by a POSIX shell. 

I should note here that `lxc-docker-1.9.1` package, shipped with contemporary Ubuntu distribution, seems to be broken a bit. The issues I've run into are that **docker** service installed by Vagrant **docker** provision doesn't:

1. start during system boot

2. read Docker daemon command line options from a conventional `/etc/default/docker` file

The first issue can be fixed simply by running:

```
sudo systemctl enable docker
```

after installation. It's possible to do so by placing another **shell** provisioner in our **Vagrantfile** (this time, after a **docker** provisioner,) but it would be very nice, if Vagrant were to fix this without need of such clumsy solutions. 

In order to address the second issue, you should edit file named `/lib/systemd/system/docker.service` and then reload **systemd** configuration. You can find some ideas how to patch this file in [the previous post](-).

The full source of the files described above can be found at [my github page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/vagrantDockerProvisioner).

------

Tested against `CentOS 7.1.1503`, `Ubuntu 15.04`, `Vagrant 1.7.4`