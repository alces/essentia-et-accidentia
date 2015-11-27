---
layout: post
title: 'Working With Gitlab Through its REST API'
date: 2015-11-27 11:49
comments: true
categories: [Python, rest, GitLab]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-20.jpg)

Gitlab has its own [REST API](https://github.com/gitlabhq/gitlabhq/blob/master/doc/api/README.md), as many other decent web applications do. This article demonstrates basic usage of this API: creating an issue and assigning this issue to a user.

In order to talk with our gitlab server via REST API, we have to learn first **private token** of gitlab user we're planning to operate on behalf of. Every gitlab user can view its own private token on *http://your.gitlab.server/profile/account* page. If you have access to your gitlab instance's PostgreSQL database, you can find these tokens in `authentication_token` column of `users` table. Since we've learnt this token, we can prepare necessary HTTP headers for all our future requests:

```
authHeaders = {
	'Content-Type': 'application/json',
	'PRIVATE-TOKEN': 'acKByNW4GYzDiY7aHjNT'
}
```

Making connection to our gitlab server will be as simple as that:

```
import httplib

gitlab = httplib.HTTPConnection('git.example.com')
```

Gitlab REST API refers to gitlab's objects not by their human-readable names but by ids (in a way similar to how joins between tables in every relational database work.) So, before we'll be able to create an issue, we should find target project's id by its name (let's say our project has a bit foolish name *GROUP01/TEST_PRJ01*):

```
gitlab.request('GET', '/api/v3/projects', headers = authHeaders)
gitResp = gitlab.getresponse()

assert gitResp.status == 200
```

HTTP response code **200** signalizes that your **GET** request has finished okay. Every other response code would mean some kind of trouble during request (e.g., **401** stands for missing or incorrect private token.)

Response from gitlab server is a JSON data, so it's not difficalt to parse it and extract **id** field from the first element of projects' list (names of projects are unique in a given namespace, so if an object with our stupid name exists, we'll get a list of one element):

```
import json

projId = map(lambda prj: prj['id'], 
	filter(lambda prj: prj['name'] == 'TEST_PRJ01' and prj['namespace']['name'] == 'GROUP01',
		json.loads(gitResp.read())))[0]
```

After learning the project id, we have all the information requred to actually create an issue:

```
gitlab.request('POST', 
	'/api/v3/projects/%d/issues' % projId,
	body = json.dumps({'id': projId,
		'title': 'Gentle reminder',
		'descrition': 'This strange thing happens again!',
		'labels': 'urgent,error'}),
	headers = authHeaders)
gitResp = gitlab.getresponse()

assert gitResp.status == 201
```

Note that this time we send a **POST** request instead of a **GET** one, and put a dict with data we want to insert into a body of this request. The only correct response status for **POST** request in gitlab REST API is **201**. Also, a successful insert returns JSON data we've just inserted, so it's possible to learn id of a newly created object without much fuss:

```
issueId = json.loads(gitResp.read())['id']
```

By this point, we already know ids for our project and issue, but in order to assign the issue we have to know one id more. Let's learn id of the user we're going to assign the issue to. Fortunately, gitlab knows how to search for a user out of box, so our task becomes a lot easier:

```
gitlab.request('GET',
	'/api/v3/users?search=jane.doe@example.com',
	headers = authHeaders)
gitResp = gitlab.getresponse()

assert gitResp.status == 200
userId = json.loads(gitResp.read())[0]['id']
```

And for now, we can assign our issue by sending a **PUT** request (it would be possible to create an issue and assign it during one operation, but I'd like to separate these two actions in order to show you a way to change an already existing gitlab object):

```
gitlab.request('PUT',
	'/api/v3/projects/%d/issues/%d' % (projId, issueId),
	body = json.dumps({'id': projId,
		'issue_id': issueId,
		'assignee_id': userId}),
	headers = authHeaders)
gitResp = gitlab.getresponse()

assert gitResp.status == 200
```
Response status for successful **PUT** request in gitlab REST API is (quite surprisingly) **200**. Also, JSON data containing the object we've just changed is returned in a body of the reponse, but our mission is already successfully completed (if it wasn't completed, we wouldn't get a **200** status), so parsing this JSON makes no practical sence.

----

Tested against `gitlab-ce-8.1.0` and `Python 2.7.8`