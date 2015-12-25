---
layout: post
title: 'Finding out Who Has Right to Do What in Nexus'
date: 2015-12-16 11:26
comments: true
categories: [Python, xml, Nexus]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-22.jpg)

If you use [Sonatype Nexus™](http://www.sonatype.com/nexus/try-compare-buy/free-downloads) in order to store your build artefacts and have quite a few repositories, roles, and users configured in it, you likely have already become disillusioned about its web interface. For example, it's not difficult to see a list of the roles a given user has, but there's no way to see a list of the users having a given role. In this article, we're going to write a Python script parsing Nexus XML configuration file and printing out a list of the users and roles having a given privilege (**privilege** in the Nexus' jargon is the permission to do something with the artifacts stored in the certain repository.) 

Nexus stores its data about users' and groups' rights in a file named `security.xml` and placed in `${nexus-work}/conf` directory. Figuring out what **nexus-work** is equal to in your particular case is a bit tricky, so let's consider its possible values for the different kinds of Nexus setup:   

* If you run Nexus atop its built-in Jetty application server, **nexus-work** by default is `${bundleBasedir}/../sonatype-work/nexus` (where `bundleBasedir` is the directory you've unzipped Nexus distribution archive into), and can be changed by assigning `nexus-work` property in `${bundleBasedir}/conf/nexus.properties` file to a desired value.

* In the case of Nexus installed as a WAR file atop an application server such as Tomcat or Glassfish, **nexus-work** will be `${user.home}/sonatype-work/nexus` by default (where **user.home** is the home directory of the user the application server is running on behalf of.) In order to change this value, look for `nexus.properties` file in `WEB-INF/classes` subdirectory inside the directory in which your application server has unrolled Nexus WAR file.

So, let's say, we've already learnt where our `security.xml` file is, and I'll reference the path to it simply as **xmlPath**. Also, the name of the privilege we're going to investigate about will be metioned as **privName**. The first thing we've to do is to parse the XML file into an Element Tree:

```
import xml.etree.ElementTree as etree
xmlRoot = tree.parse(xmlPath).getroot()
```

Here's an example of XML sctructure Nexus uses to store data about privileges:

```
</privileges>
    <privilege>
      <id>2b9dd27ca3889</id>
      <name>huge_project.snapshots - (update)</name>
      <description>huge.project.snapshots</description>
      <type>target</type>
      <properties>
        <property>
          <key>method</key>
          <value>update,read</value>
        </property>
        <property>
          <key>repositoryId</key>
          <value>huge_project.snapshots</value>
        </property>
        <property>
          <key>repositoryTargetId</key>
          <value>any</value>
        </property>
      </properties>
    </privilege>
    ...
</privileges>
```

All the references to the privilege in other places of the XML tree are made by its id, so we've to find out, which id corresponds to **privName**. In order to don't repeat ourselves too much, let's create a few simple helper functions. The first one just extract id from an XML element describing a privilege (it also can be used with other XML elements having a subelement named 'id' inside them):

```
getId = lambda anElem: anElem.find('id').text
```

It would be very unlikely to run into coincident when two different privileges have the same name, so we can get a list of privileges with a given name and believe that its fist element is what we're looking for:

```
import sys

privs = [getId(priv)
	for priv in xmlRoot.findall('./privileges/privilege')
	if priv.find('name').text == privName]
if privs:
	privId = privs[0]
else:
	sys.stderr.write("Privilege named '%s' isn't found\n" % privName)
	sys.exit(1)
```

Privileges in Nexus can't be assigned directly to users (only to roles), so our next task is to find roles having a privilege with id equal to **privId** we've found at the previous step. Sample structure of a role's record in `security.xml` looks like this:

```
<roles>
	<role>
		<id>snapshots_rw</id>
		<name>snapshots_rw</name>
		<description>R/W access to snapshots repositories</description>
		<privileges>
			<privilege>241fa1ad2410e</privilege>
			<privilege>289db22c2493c</privilege>
			...
		</privileges>
		<roles>
			<role>huge_project.snapshots_rw</role>
			<role>tiny_project.snapshots_rw</role>
			...
		</roles>
	</role>
</roles>
```

Our second helper function returns a list of privileges a given role has:

```
def rolesPrivs(role):
	return [priv.text for priv in role.findall('./privileges/privilege')]
```

Having these two functions written, we can get a list of roles having our privilege as simply as that:

```
primRoles = [getId(role)
	for role in xmlRoot.findall('./roles/role')
	if privId in rolesPrivs(role)]
```

Here and bellow I imply that role's ids have values having some meanings for human beings - simply because it was done this way in all the Nexus installations I've seen in the real world. Otherwise, you likely would like to use a dictionary of roles' names indexed by their ids instead of a plain list.

For now, the task of finding roles having a given privilege isn't completed yet. As you might have already noticed, roles in Nexus can have roles by themself, so we've to look for all the roles having the roles from **primRoles** list and then all the roles having those roles recursively. Let's continue writing few helper functions more. The next one returns a list of all the roles a given XML element has:

```
def getRoles(anElem):
	return [role.text for role in elem.findall('roles/role')]
```

And the last helper function for today goes by Nexus roles' tree recursively and returns a nested list of roles having the role with a given name:

```
def whoHasRole(roleName):
	return [(subRole, whoHasRole(getId(subRole)))
		for subRole in xmlRoot.findall('./roles/role')
		if roleName in getRoles(subRole)]
```

Next, we make this list flat by [the function described in this blog earlier](http://essentia-et-accidentia.logdown.com/posts/335120) and make the final list of all the roles effectively having the privilege we're interested in:

```
secRoles = map(getId,
	flatten([whoHasRole(role) for role in primRoles]))
allRoles = set(primRoles) | set(secRoles)
```

By this point, all we have to do is to find the users having roles from our list. Users' bindings to roles are stored in the structures like this one:

```
<userRoleMappings>
	<userRoleMapping>
		<userId>hudson</userId>
		<source>LDAP</source>
		<roles>
			<role>huge_project.snapshots_rw</role>
			<role>tiny_project.snapshots_rw</role>
			<role>build_tools_ro</role>
			...
		</roles>
	</userRoleMapping>
	...
</userRoleMappings>
```

Note that the user's roles are described in the same form as the role's roles we've seen before, so we can use our **getRoles** helper function once again:

```
users = [user.find('userId').text
	for user in xmlRoot.findall('./userRoleMappings/userRoleMapping')
	if allRoles & set(getRoles(user))]
```

And finally, let's print our results in a form readable for humans:

```
if allRoles:
	print 'Roles:\n\t' + '\n\t'.join(sorted(allRoles))
if users:
	print 'Users:\n\t' + '\n\t'.join(sorted(set(users)))
```

(note that the users' list is converted to a set, becase one user could have more than one role having the same privilege, so it would be mentioned in our list many times)

The full source of the script described above can be found on [my gitlab page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/nexusWhoHasPrivilege.py).

----

Tested against `Sonatype Nexus™ 2.11.4-01`, `Python 2.7.8`