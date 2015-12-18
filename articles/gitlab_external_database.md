---
layout: post
title: 'Running Gitlab with an External Database'
date: 2015-12-18 10:49
comments: true
categories: [PostgreSQL, GitLab, Ansible]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-62.jpg)

Gitlab RPM already contains a full-blown PostgreSQL distribution inside (on the time of writing, version 9.2.10 is used) and by default runs PostgreSQL database locally in order to store all kinds of the users', groups', issues', merge requests', etc. data in it. But what if you already have a well-tuned database server, or maybe you're going to run multiple Gitlab instances behind a load balancer and share the same database between them? Currently, the only kind of external database Gitlab Community Edition can work with is PostgreSQL (according to [this document](https://gitlab.com/gitlab-org/omnibus-gitlab/blob/master/doc/settings/database.md), Gitlab Enterprise Edititon also supports MySQL and MariaDB.) This article describes how to configure Gitlab and a brand new version of PostgreSQL server (installed as described on [the project's site](http://www.postgresql.org/download/linux/redhat/)) running on another host to work together.

Let's use a simple Ansible playbook in order to tell our PostgreSQL server to permit access from our Gitlab server(s) to the newly created database named `gitlabhq`. Imagine, you have `gitlab` group in the Ansible inventory containing all the servers you're planning to run Gitlab on and `pgsqldb` group containg your database server(s):

```
---
- name: Provide Gitlab with access to PostgreSQL 
  vars:
    gitlab_user: gitlab
    gitlab_pass: PaSsW0rd4gitLab
    gitlab_db: gitlabhq
    pg_ver: 9.4
    pg_data_dir: "/var/lib/pgsql/{{ pg_ver }}/data"
    pg_service: "postgresql-{{ pg_ver }}"
  hosts: pgsqldb
  sudo: true
  tasks: 
  - name: Add PostgreSQL user for gitlab
    postgresql_user:
      name: "{{ gitlab_user }}"
      password: "{{ gitlab_pass }}"
    sudo_user: postgres
  - name: Add database for gitlab
    postgresql_db:
      name: "{{ gitlab_db }}"
      owner: "{{ gitlab_user }}"
      encoding: UTF-8
    sudo_user: postgres
  - name: Open access to PostgreSQL from gitlab host
    lineinfile:
      dest: "{{ pg_data_dir }}/pg_hba.conf"
      line: "host  {{ gitlab_db }}  {{ gitlab_user }}  {{ item }}/32  md5"
    with_items: "{{ groups.gitlab }}"
    notify: restart postgresql
  handlers:
  - name: restart postgresql
    service:
      name: "{{ pg_service }}"
      state: restarted
```

On the Gitlab's side, we first should disable its internal PostgreSQL server by setting `postgresql['enable']` in `/etc/gitlab/gitlab.rb` to `false`.

Next, let's add parameters of the external database connection to the same file:

```
gitlab_rails['db_database'] = "gitlabhq"
gitlab_rails['db_username'] = "gitlab"
gitlab_rails['db_password'] = "PaSsW0rd4gitLab"
gitlab_rails['db_host'] = "the.pg.sql.server"
```

When our business with `gitlab.rb` is finished, we must regenerate configuration files for all the diffent Gitlab services (nginx, unicorn, sidekiq, etc. -  all stored under `/var/opt/gitlab`) from the templates stored under `/opt/gitlab/embedded/cookbooks` according to the parameters we've just changed by running:

```
gitlab-ctl reconfigure
```

(on a newly installed Gitlab server this command often have to be run twice in sequence)

Under normal circumstances, when `gitlab-ctl reconfigure` successfully finishes its job, Gitlab will be fully operational. But if you've replaced a local PostgreSQL database by a remote one, it might be not the case (although, output of `gitlab-ctl reconfigure` looks like it has done its job okay.) As far as I know, Gitlab will populate a remote database with tables and other objects only if:

1. it has never use a local database before (i.e., you run `gitlab-ctl reconfigure` for the first time on this host after making the aforementioned changes in `gitlab.rb`), or

2. you have explicitly said it to do that.

In the first case you're lucky. Otherwise, if you already have a dump of an existing Gitlab database, you could roll it out on the empty `gitlabhq` database on the PostgreSQL server, but if your intention is to start from the clean slate, you should run the following command to create initial database structure:

```
gitlab-rake gitlab:setup
```

(even in a case of having multiple Gitlab servers sharing the same database, this command must be run only once)

By now, when we have a some kind of correct data in `gitlabhq` database, Gitlab must be restarted in order to begin working with this database:

```
gitlab-ctl restart
```

-----

Tested against `ansible 1.9.4`, `gitlab-ce-8.2.1`, and `PostgreSQL 9.4.5`