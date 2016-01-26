---
layout: post
title: 'Employing your Python Skills while Writing Ansible Playbooks'
date: 2016-01-26 10:45
comments: true
categories: [Python, jinja2, Ansible]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-11.jpg)

From a technical point of view, Ansible playbooks are YAML files with variables' substitutions implemeted via Jinja2 templates. In case you want to make some manipulations with this variables besides simple values' sustitution, Jinja2 has support of some kind of functions called 'filters'. Syntax of their invocation reselmbles that for plain old shell pipes and described in details [here](http://jinja.pocoo.org/docs/dev/templates/#list-of-builtin-filters). But, as long as Jinja2 variables present Python objects, it's also possible to manipulate with them using the experience you've gaining during writing scripts in Python. The following article describes some of this Pythonic hacks available for an Ansible playbook writer.

In order to start from simple things, let's demonstrate that Ansible variables assigned to string values support the same methods Python **str** class does. For example, you can convert a string to the upper case:

```
  vars:
    env_name: prod
  tasks:
  - name: Convert a string to upper case
    debug:
      msg: "env={{ env_name.upper() }}"  
```

or test whether a string starts with a given substring (also note that Python ternary operator works as expected):

```
  vars:
    jdk_ver:  1.8.0_51
  tasks:
  - name: Simple string matching
    debug:
      msg: "Your Java is {{ 'up to date' if jdk_ver.startswith('1.8') else 'outdated' }}"
```

String operators also work. So, it'll be possible to concatenate strings:

```
  vars:
    java_root: /opt
    jdk_ver:  1.8.0_51
  tasks:
  - name: Concatenate strings
    debug:
      msg: "java_home={{ java_root + '/jdk' + jdk_ver }}"
```

or to insert formatted values into a string using **%** operator (and I should admit, it's the my favorite one in Python):

```
  vars:
    java_root: /opt
    jdk_ver:  1.8.0_51
  tasks:
  - name: Percent operator with a tuple
    debug:
      msg: "java_bin={{ '%s/jdk%s/bin' % (java_root, jdk_ver) }}"
  - name: Percent operator with a dict
    debug:
      msg: "java_bin={{ '%(root)s/jdk%(ver)s/bin' % {'ver': jdk_ver, 'root': java_root} }}"

```

Note that we define such Python types as tuple and dictionary using exactly the same form we would use in Python.

If you rather want to follow the latest Python fashion and use **format** method instead of **%** operator, you can do so in Ansible as well:

```
  vars:
    java_root: /opt
    jdk_ver:  1.8.0_51
  tasks:
  - name: format() method with anonymous arguments
    debug:
      msg: "java_bin={{ '{}/jdk{}/bin'.format(java_root, jdk_ver) }}"
  - name: format() method with named arguments
    debug:
      msg: "java_bin={{ '{root}/jdk{ver}/bin'.format(ver = jdk_ver, root = java_root) }}"
```

A string also can be splitted in a list, and an element from this list can be selected by its index:

```
  vars:
    jdk_ver:  1.8.0_51
  tasks:
  - name: Splitting a string
    debug:
      msg: "java_major_version={{ jdk_ver.split('.')[0] }}"
```

But, if you want to calculate the next Java version by writing somethig like that:

```
next_java_minor_version={{ jdk_ver.split('.')[1] +1 }}
```

it won't work because Ansible never converts strings to numbers automatically (in a full concordance with an original Python behaviour.) The following code does work, because here we assign our variable to an interger value:

```
  vars:
    rhel_major_version: 7
  tasks:
  - name: Adding numbers
    debug:
      msg: "rhel_next_version={{ rhel_major_version + 1 }}"
```

But that one doesn't work, because we have no Python built-in functions available in Ansible:

```
next_java_minor_version={{ int(jdk_ver.split('.')[1]) +1 }}
```

In order to convert a string into integer, instead of Python **int** function we must you the Jinja2 filter with the same name, but working with Jinja2 filters is out of scope of the article.

Variables defined in Ansible playbook can be not only the strings and integers but the lists and dictionaries too. They can be defined using a YAML-like way:

```
vars:
    # sample dict
    linux_distros:
      ubuntu: 14.04
      debian: sid
      redhat: 7.1
      slackware: 14.1
    # sample list  
    bsd_distros:
    - freebsd
    - openbsd
    - netbsd  
```

or using in-line Python-like syntax:

```
vars:
  developers: [Mary Smith, Johnny Reb, Billy Yank]
  languages: {Mary Smith: java, Johnny Reb: python, Billy Yank: ruby}
```

Note that quotes aren't required even if the keys or values contain spaces.

Lists' elements can be addressed by their indexes (you've already seen the syntax), values of the dictionaries - by their keys. Both the classical Pythonic syntax using square-brackets and the short syntax using dots are supported:

```
  - name: Getting dict element by [] operator
    debug:
      msg: "redhat_version={{ linux_distros['redhat'] }}"

  - name: Getting dict element by dot operator
    debug:
      msg: "ubuntu_version={{ linux_distros.ubuntu }}"
```

Note that quotes around a key are required in the first case and forbidden in the second one.

All the standard Python methods and operators (and the true Python guys know that they're essentially the methods too) work as expected. For example, we can get a list of dictionary's keys and concatenate this list with another:

```
  - name: Getting dict's keys and concatenating lists
    debug:
      msg: "{{ item }}"
    with_items: "{{ linux_distros.keys() + bsd_distros }}"
```

But dont' be too sanguine, because one couldn't say that everything works in Ansible the same way it works in Python. No built-in functions (yes, it means that no **map**, **filter**, and **reduce**), no list comprehensions. So, you couldn't do whatever you want going down the Python way only. Jinja2 own filters and command structures still worth studying.

----

Tested against `ansible 1.9.4`