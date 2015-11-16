---
layout: post
title: 'Some Useful Decorators for Tasks in Fabric'
date: 2015-11-16 09:59
comments: true
categories: [Python, fabric, decorators]
---
![](https://upload.wikimedia.org/wikipedia/commons/thumb/4/40/Gustave_Dor%C3%A9_-_Miguel_de_Cervantes_-_Don_Quixote_-_Part_1_-_Chapter_1_-_Plate_1_%22A_world_of_disorderly_notions%2C_picked_out_of_his_books%2C_crowded_into_his_imagination%22.jpg/791px-thumbnail.jpg)

Technically speaking, you can happily write your [fabfiles](http://www.fabfile.org/) without any knowledge about decorators at all. While using such sort of setup, all the functions defined in **fabfile.py** will also be tasks and can be called from command line. For example, if you wrote such (intentionally oversimplified) **fabfile.py** with two functions without arguments:

```
from fabric.api import run

def cleanup():
	run('''echo "let's cleanup first!"''')

def deploy():
	run('''echo "let's do what matters!"''')
```

running `fab -H someserver cleanup deploy` from a directory where the **fabfile.py** is saved would result in executing those two tasks in sequence:

```
[someserver] Executing task 'cleanup'
[someserver] run: echo "let's cleanup first!"
[someserver] out: let's cleanup first!
[someserver] out: 

[someserver] Executing task 'deploy'
[someserver] run: echo "let's do what matters!"
[someserver] out: let's do what matters!
[someserver] out: 

Done.
Disconnecting from someserver... done.
```
Moreover, if you have a function of let's say two arguments such as this one:

```
def deploy(env_name, server_port):
	run('''echo "let's deploy a server in enviroment %s listenning on port %s!"''' % (env_name, server_port))
```

then you can pass arguments to the task this way (by position): 

```
$ fab -H someserver deploy:prod,8080
[someserver] Executing task 'deploy'
[someserver] run: echo "let's deploy a server in prod enviroment listenning on port 8080!"
```

or even this way (by name):

```
$ fab -H someserver deploy:server_port=8443,env_name=stage
[someserver] Executing task 'deploy'
[someserver] run: echo "let's deploy a server in stage enviroment listenning on port 8443!"
```

(standard Python syntax for defining defaults values of functions' arguments also works as expected)

But what if you want to put into your **fabfile.py** some utility functions you're not going to run from command line? What if you're planning to start with one particular task for 90% of time? Wouldn't it be nice to do so without need to explicitly name this task in command line? So let's learn about our first decorator<sup>1</sup>.

## @task

The syntax should be familiar for everyone who has ever written a couple of lines in Java 5 or later:

```
@task
def deploy():
	run('''echo "let's do deployment!"''')
```

Since any one function in your **fabfile.py** is decorated with `@task`, you couldn't call any non-decorated function from command line, but you still were able to call them from other tasks as ordinary Python functions.

If one of the tasks in a natural entry point into a prodecure you've coded in **fabric.py**, it'll be wise to set `default` parameter in its decorator equal to `True`:

```
@task(default = True)
def cleanup():
	run('''echo "let's clean up first!"''')
```

From now, if you execute `fab` command without mentioning a task name, it'll start by this task. Obviously, it makes no sense to have more than one default task, although fabric (at least, its version 1.9.0 I've tested all the code from this article against) doesn't complain about it. If at least one task is explicitly named in command line, `default` parameter will be ignored.

## @hosts, @roles, and @runs_once

Let's imagine, you're planning to install nginx on one pool of servers, mysql on the other, and finally run some cleanup routines on the affected servers. In order to do so you could create the following set of tasks decorated with `@hosts` decorator:

```
db_hosts = ['db01', 'db02', 'db-test']
web_hosts = ['www01', 'www02', 'www-test', 'www-lb']

@task(default = True)
def start_depoly():
	execute('install_db')
	execute('install_www')
	execute('cleanup')

@task
@hosts(db_hosts)
def install_db():
	run('apt-get install mysql-server')
  
@task
@hosts(www_hosts)
def install_www():
	run('apt-get install nginx')
  
@task
@hosts(db_hosts + www_hosts)
def cleanup():
	run('rm -rf /tmp/installDir')
```

Argument of `@hosts` can be a comma-separated list of strings or a value of any iterable type (e.g. if you have two lists named `our_hosts` and `their_hosts` defined in your **fabfile.py**, it'll be possible to write something like this: 

```
@hosts(set(our_hosts) & set(their_hosts))
```

If `env.roledefs` dictionary is defined somewhere in preamble of the **fabfile.py**, you can also use `@roles` decorator. For example:

```
env.roledefs = {'db': ['db01', 'db02'], 'www': ['www01', 'www02']}

@task
@roles('db')
def install_db():
	run('apt-get install mysql-server')

@task
@roles('db', 'www')
def cleanup():
	run('rm -rf /tmp/installDir')
```

While the tasks are decorated with `@hosts` or `@roles`, the arguments of these decorators will have precedence over values of `-H` and `-R` flags entered in a command line.

It's not difficult to imagine a task that must not run twice during one deployment (e.g. a task starting the whole process, or maybe a task consisting only of local operations on administator's box.) You can do so by adding `@runs_once` decorator. It works either with `@hosts` or `@roles` decorators or without them. For example, if `fab -H srv01,srv02,srv03` was typed, the tasks decorated with `@runs_once` would run only on **srv01**.

## @parallel and @serial

Sometimes executing of long tasks on many servers takes a lot of time, but it's possible to speed things a bit by adding `@parallel` decorator at the top of these tasks. For example, let's create such a primitive but not rocket-fast procedure:

```
@task
@parallel
def deploy():
	run('echo "depoy is started"')
	run('sleep 30')
	run('echo "deploy is finished"')
```

If you ran this task by typing `time fab -H srv01,srv02 deploy` in command line, you would see the follow:

```
[srv01] Executing task 'deploy'
[srv02] Executing task 'deploy'
[srv02] run: echo "depoy is started"
[srv01] run: echo "depoy is started"
[srv01] out: depoy is started
[srv01] out: 

[srv01] run: sleep 30
[srv02] out: depoy is started
[srv02] out: 

[srv02] run: sleep 30
[srv01] run: echo "deploy is finished"
[srv02] run: echo "deploy is finished"
[srv02] out: deploy is finished
[srv02] out: 

[srv01] out: deploy is finished
[srv01] out: 

Done.

real	0m31.369s
...
```

While without `@parallel` decorator output would be a bit different (and execution would take a bit longer):

```
[srv01] Executing task 'deploy'
[srv01] run: echo "depoy is started"
[srv01] out: depoy is started
[srv01] out: 

[srv01] run: sleep 30
[srv01] run: echo "deploy is finished"
[srv01] out: deploy is finished
[srv01] out: 

[srv02] Executing task 'deploy'
[srv02] run: echo "depoy is started"
[srv02] out: depoy is started
[srv02] out: 

[srv02] run: sleep 30
[srv02] run: echo "deploy is finished"
[srv02] out: deploy is finished
[srv02] out: 

Done.
Disconnecting from srv01... done.
Disconnecting from srv02... done.

real	1m2.174s
...
```

If parallel execution is turned on by for all tasks (either by adding `--parallel` flag to a command line or by setting `env.parallel = True` in **fabfile.py**), you can force serial execution of some particular tasks by decorating them with `@serial` decorator.

Official documentation on all fabric decorators can be found on [the vendor site](http://docs.fabfile.org/en/1.10/api/core/decorators.html?highlight=decorator#module-fabric.decorators).

----
<sup>1</sup> All the decorators mentioned in the article lives in `fabric.api` module, wherefrom they can be imported en masse:

```
from fabric.api import *
```

or individually:

```
from fabric.api import task, hosts, parallel
```