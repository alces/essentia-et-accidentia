---
layout: post
title: 'Meet CoreOS: a Bare-back Linux for Running Docker on'
date: 2016-01-26 09:56
comments: true
categories: [PostgreSQL, vagrant, docker, Container, CoreOS]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-34.jpg)

As long as such a revolutionary method of deploying software on Linux servers as Docker containers emerges, Linux by itself should change too, because many of its capabilities - no matter how widely celebrated in the past - seems now redundant and unnecessary. So let's take a look at a representative of this new breed of Linux named **CoreOS**. According to its [official site](https://coreos.com/), the main purpose of this project is to make Linux distribution for using in the massive cloud deployments, but you can become acquainted with the new guy on the block by running it locally over Vagrant. This way of installing CoreOS is officially supported: the official site has [an instruction](https://coreos.com/os/docs/latest/booting-on-vagrant.html) on the topic, which - I should admit - looks a bit daunting for an uninitiated person. Fortunately, there's a lot simpler way to get first look at the new OS: run it from a Vagrant box already prepared and stored at https://atlas.hashicorp.com. For example, [this one](https://atlas.hashicorp.com/AntonioMeireles/boxes/coreos-alpha) contains a pretty fresh version of the subject (at the time of writing, it was released only 3 days ago.)

The minimal viable **Vagrantfile** for CoreOS guest looks this way:

```
Vagrant.configure(2) do |cfg|
  cfg.vm.box = 'AntonioMeireles/coreos-alpha'
end
```

Or, alternatively, you can simply run `vagrant init AntonioMeireles/coreos-alpha` into an empty directory, and a similar **Vagrantfile** (with a lot of additional comments) will be generated automatically.

At the first sight, the main difference from the Vagrant virtual machines we've used to are:

1. a user you're logging on behalf of when using `vagrant ssh` is named **core** instead of the familiar **vagrant** (but it still has permissions for passwordless sudo,)

2. shared folder `/vagrant` isn't mounted to a virtual machine (it looks like, shared folders aren't supported at all,) but, fortunately, **docker** provisioner can work well without it (**file** and **shell** provisioners also can get their job done without any use of shared directories.)

This virtual machine already has a fresh Docker-1.9.1 on board, so we can deploy, for example, an official PostgreSQL container on it as simple as that:

```
Vagrant.configure(2) do |cfg|
  cfg.vm.box = 'AntonioMeireles/coreos-alpha'
  cfg.vm.network :private_network, ip: '192.168.1.101'
  cfg.vm.provision :docker do |dock|
    dock.run 'postgres', args: "-p 5432:5432 -e POSTGRES_PASSWORD=pg123456"
  end
end
```

As you can see from the listing, our PostgreSQL server exposes its standard 5432 into the private host network. Environment variable named **POSTGRES_PASSWORD** stands for a password for PostgreSQL admin user (for those who doesn't know yet, the name of this user is **postgres**.) The other environment variables supported by this container are described in details [here](https://hub.docker.com/_/postgres/).

I believe, you're already aware that it's strongly unrecommended to save any data inside application containers, so we're going to teach our PostgreSQL how to store its database files in a directory outside the container. The most intuitive way to do this is to create a directory on our virtual machine and mount it to the container as a volume. In the case you want to start from a clean state, Docker is clever enough to create a directory the volume points out, and PostgreSQL init script is clever enough to set the requred ownership and permissions to this directory. So, we only thing we really have to do is to add `-v` option to our call of `dock.run` method:

```
pg_data = '/var/lib/postgresql/data'
dock.run 'postgres', args: "-p 5432:5432 -v #{pg_data}:#{pg_data} -e POSTGRES_PASSWORD=pg123456"
```

But, if you already have a database directory you want to expose to the PostgreSQL container, it's necessary to change its ownership so that PostgreSQL daemon running inside the container will have read/write access to it. In order to do so, you must first learn id the **postgres** user inside our container has. This can be achieved by running `id postgres` command inside the container having a given name (by default, **run** method of the **docker** provisioner gives to a container the same name its base image has):

```
docker exec $(docker ps | awk '/ postgres$/ {print $1}') id postgres
```

In my case, the output was the following:

```
uid=999(postgres) gid=999(postgres) groups=999(postgres),109(ssl-cert)
```

So, the owner id for `/var/lib/postgresql/data` on our virtual machine should be changed accordigly:

```
chown -R 999:999 /var/lib/postgresql/data
```

Note that we may not have **postgres** user on the machine running Docker daemon (or might be have this user with another id,) because - as it has always been on Unix-like systems - in dealing with file permissions only numeric ids matter.

This approach works quite well, but - according to, for example, [this document](http://stackoverflow.com/questions/23544282/what-is-the-best-way-to-manage-permissions-for-docker-shared-volumes) - the only proper way to store data for Docker containers is by creating the so-called data volume containers. Let's create such kind of container atop of **busybox** image. First, we should create a **Dockerfile** inside the same directory where our **Vagrantfile** is. Its content will be quite minimalistic:

```
FROM busybox
MAINTAINER Alexey Zalesnyi
VOLUME /var/lib/postgresql/data
```

Note that the only thing we actually have to do in this file is creating a volume for PostgreSQL database. Certainly, the standard **busybox** image doesn't content a directory with such a path, but the directory will be created by `docker build`, and proper permissions and ownership for it will be set by PostgreSQL init script.

Next, in order to put this file on our CoreOS host we have to employ Vagrant **file** provisioner:

```
build_dir = '/var/tmp'
Vagrant.configure(2) do |cfg|
  cfg.vm.provision :file, source: 'Dockerfile', destination: "#{build_dir}/Dockerfile"
```

then let's build an image from this file (**docker** provisioner knows how to do that):

```
  cfg.vm.provision :docker do |dock|
    dock.build_image build_dir, args: '-t pgdata'
```

and start a container using this image:

```
    dock.run 'pgdata', cmd: true
```

Note that - as long as we create a Docker container the only purpose of which is keeping volumes for other containers - it actually can do its job without being started at all (it only must exist.) But Vagrant's **docker** provisioner currently has no method implementing `docker create` subcommand, so we use its **run** method with a short-living command (and `/bin/true` is certainly a command finishing its job very quickly) in order to just create it.

The only thing left is to mount this volume to our **postgres** container:

```
    dock.run 'postgres', args: "-p 5432:5432  --volumes-from=pgdata -e POSTGRES_PASSWORD=pg123456"
```

In case you wonder what is exacly stored in the data container's volume, you can examine that by running another container with an interactive shell and mounting volumes from the same container to it. For example:

```
docker run -it busybox --volumes-from=pgdata sh
```

Note that we use `sh` as the command for running shell (instead of `/bin/bash` every linuxoid is used to), because **busybox** image doesn't contain an executable named **bash**.

Of course, CoreOS can do much more than simply run Docker containers out-of-box (e.g., it has built-in cluster capabilities), but its other innovative features is out of scope of this article.

The full source code of **Vagrantfile**s desribed above can be found on [my github page](https://github.com/alces/essentia-et-accidentia/tree/master/code-samples/meetCoreOS).

----

Tested against `CoreOS alpha (935.0.0)`, `Vagrant 1.7.4`