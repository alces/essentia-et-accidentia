---
layout: post
title: 'How to Make Jenkins Git Plugin Work via HTTPS with "Incorrect" SSL Certificate'
date: 2015-11-09 09:49
comments: true
categories: [git, java, Https, jenkins, Groovy, Keytool]
---
![](https://upload.wikimedia.org/wikipedia/commons/9/91/GustaveDore_She_was_astonished_to_see_how_her_grandmother_looked.jpg)

If your Jenkins server works with the git server via HTTPS and the latter uses a self-signed (or might be seeming incorect in some other sense) SSL certificate, you'll likely get an error like this:

```
hudson.plugins.git.GitException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

In order to get rid of it the steps bellow could be helpful (at least, they were helpful for me while setting up Jenkins build nodes on both Linux and Windows boxes):

## 1. Where does our Java live?

Let's determine, where's the home of Java your build node is run by. If there're multiple Java installations on your build server (and I think, that's a common case for such sort of servers), you might have some doubts about which of them actually runs your `slave.jar`. In order to find a precise answer go to the node's Script Console at http://**YOUR_CI_SERVER**:8080/computer/**YOUR_NODE_NAME**/script (surely, if you use the non-standard port or context name for your Jenkins installation change URL accordingly) and get a result of this command: `System.getProperty("java.home")`

## 2. Add git's cetrificate to a trusted store

Then let's export your git server's SSL certificate into `myserver.crt` file (all commonly-used browsers   know how to do that - just right-click on a "lock" icon near URL), copy it to the server your node run on, and import it   in your Java's keystore (**$JAVA_HOME** in the command bellow would be the result you've got on the step 1): 

```
$JAVA_HOME/bin/keytool -importcert -alias myserver -file myserver.crt \
-keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
```
(on a Windows box, of course, use backslashes instead of the forward ones)

If you want to attest whether the desired certificate is already in a target keystore, it can be looked for by its alias: 

```
$JAVA_HOME/bin/keytool -list -keystore $JAVA_HOME/lib/security/cacerts \
-storepass changeit | grep -C1 myserver
```

or by its SHA1 fingerprint:
 
```
$JAVA_HOME/bin/keytool -list -keystore $JAVA_HOME/lib/security/cacerts \
-storepass changeit | grep -C1 PA:RT:OF:MY:SH:A1
```

## 3. Where's the home of our JVM's user?

For now, we should learn the home directory of the user your node's JVM runs on behalf of. In case of any doubts about that go to the Script Console (see step 1 for its URL) for your node and remember a result of running this one-liner: `System.getProperty("user.home")`

## 4. Tell git to trust our certificate

Then go to the directory you learned at the previous step and add the following lines to `.gitconfig` file:

```
[http]
	sslVerify = false
```
 
(if `~/.gitconfig` file doesn't exists, create one containing only two lines shown above)

----

Tested against `Jenkins 1.565.3`, `Git plugin 2.2.1`, and `gitlab-ce-8.1.0`