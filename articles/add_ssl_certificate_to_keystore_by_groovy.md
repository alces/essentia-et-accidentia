---
layout: post
title: 'Adding an SSL Certificate to JVM Trusted Store by Groovy Script'
date: 2015-12-22 11:58
comments: true
categories: [Groovy, scriptler, SSLCertificate, KeyStore]
---
![](http://uploads5.wikiart.org/images/gustave-dore/don-quixote-2.jpg)

If there're a lot of JDK versions and JVM-based application installed on the hosts administered by you, and self-signed SSL certificates (or maybe certificates with wildcards in CN field, which looks okay for most contemporary browsers, but are despised by all the web clients written in Java) are used in your environment, then adding these 'incorrect' certificates in Java trusted stores can become your daily business. This article describes creating a Groovy script your could use to download SSL certificate directly from a site using its URL and make it trusted for multiple Java installations at once.

Of course, I'm aware that any Java distribution already has a command-line tool named `keytool` which already knows how to list certificates stored in a file, add a new certificate there, and do a lot of other similar stuff, but I have something to say about it:

* Syntax of its command line is truly cumbersome. No one can remember how to do something helpful by it without glimpsing first into Google (or maybe in the notes left from a previous use of this wonderful command.)

* It only knows how to add a certificate stored in a local file, so you have to somehow transmit your certificate into a target machine first. Also, one more unnecessary file will be left on your computer after getting job done. Think, do you really love this kind of files scattered everywhere?

* And finally, writing just another crummy shell script doesn't seem amusing to me. Everybody knows how to do that, but nobody knows how to do that well. Wouldn't it be much more funny to learn about how Java works with its trusted certificates by itself? 

If you're planning to run the following script by [Jenkins Scriptler Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Scriptler+Plugin), you should add this kind of comment containing Scriptler metadata atop it:

```
/*** BEGIN META {
	"name": "Add SSL certificate to multiple JVMs",
	"comment": "Download an SSL certificate from an HTTPS URL and add it in cacerts files of all the JVMs under a given root",
	"parameters": ["httpsURL", "javaRoot"],
	"core": "1.300",
	"authors": [
		{name : "Alexey Zalesnyi"}
	]
} END META**/
```

The most interesting line in the whole structure is the one defining **parameters** list. The first parameter means an URL to download SSL Certificate from (e.g., `https://git.example.com/`; obviously, it must has **https://** schema - trying to get an SSL certificate from a plain **http://** URL doesn't make much sence), the second one - a directory in which all the target versions of Java are installed (e.g, `/usr/local/java`.) If your intention is to run the script simply from command line (after all, it doesn't contain any Jenkins-specific parts), you could get these values using, for example, standard Groovy **CliBuilder**:

```
cli = new CliBuilder()
cli.url('https URL to get a certificate from', args: 1)
cli.dir('root directory of Java installations', args: 1)
options = cli.parse(args)

httpsURL = options.url
javaRoot = options.dir
```

Correct syntax of the script's invocation in this case would look like that: 

```
yourScriptName.groovy -url https://git.example.com/ -dir /usr/local/java
```

Having finished with the script's preamble, let's start coding its part doing the real work. The first thing we're going to do is to download SSL certificate from the URL. Some sites (e.g., a well-known https://www.google.com) return multiple SSL certificates, so we're interested only in the first non-CA certificate (i.e., the certificate of the site itself):

```
httpsSite = new URL(httpsURL)
conn = httpsSite.openConnection()
conn.connect()
urlCert = conn.serverCertificates.findAll {
    it.type == 'X.509' && it.basicConstraints == -1
}.first()
conn.disconnect()
```

Next, let's make a list of all the keystores under our **javaRoot**. In case of JDK, a default keystore is stored in a file named `jre/lib/security/cacerts` under the home of the Java installation, in case of JRE - the path is simply `lib/security/cacerts`, so we'll construct the both variants of keystore locations, flatten the list, and filter out non-existent files form it:

```
caCerts = new File(javaRoot).listFiles().findAll {
  it.directory
}.collect {dir ->
  ['', 'jre'].collect {
    new File(dir, "$it/lib/security/cacerts")
  }
}.flatten().findAll {
  it.exists()
}
```

In order to open a keystore, a password must be entered. As almost everyone knows, default password for Java keystores is **changeit**. But this knowledge isn't sufficient for our task, because according to [Java API documentation](http://docs.oracle.com/javase/7/docs/api/java/security/KeyStore.html), `load(InputStream, char[])` and `store(OutputStream, char[])` methods of `java.security.KeyStore` class want their second argument to be of type `char[]` not `String`. So, we should apply `toChars()` method to our worldwide-known string (in order to do so, we use a short form of method invocation supported by Groovy - if you don't quite understand why it works this way, look into [Groovy official documentation](http://www.groovy-lang.org/semantics.html#gpath_expressions)):

```
password = 'changeit'.chars
```

The next snippet is pretty long, so I've added three numbered headers to it. Here's description of what each of these section actually does:

1. initialize keystore and load keys from a `cacerts` file into it;

2. investigate whether the certificate downloaded from our URL is already exists in this keystore (this precaution should make our script idempotent - what a buzzword!);

3. if our certificate wasn't found at the previous step, add trust entry for it. The first argument of `setEntry` method is an entry alias, and we use domain name extracted from the URL we've downloaded our certificate from for this purpose. Next, we'll write the updated keystore back into the same file:

```
import java.security.KeyStore

caCerts.each {keyFile ->
  // 1. load certificates from a file to keystore
  keyStore = KeyStore.getInstance('jks')
  keyFile.withInputStream {
  	keyStore.load(it, password)
  }
  
  // 2. search for our certificate
  found = keyStore.aliases().findAll {
    keyStore.getCertificate(it) == urlCert
  }
  
  // 3. add our certificate and save keystore to a file 
  if (! found) {
    keyStore.setEntry(httpsSite.host,
    	new KeyStore.TrustedCertificateEntry(urlCert),
      null)
    keyFile.withOutputStream {
        keyStore.store(it, password)
    }
  }  
}
```

The full source of the script described above can be found on [my github page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/addSSLcertToMultipleJavas.groovy).

-----

Tested against `Groovy 1.8.9`