/*** BEGIN META {
	"name": "Add SSL certificate to multiple JVMs",
	"comment": "Download SSL certificate from a given HTTPS URL and add it in all Java under a root",
	"parameters": ["HTTPS_URL", "JAVA_ROOT"],
	"core": "1.300",
	"authors": [
		{name : "Alexey Zalesnyi"}
	]
} END META**/

import java.security.KeyStore

// get the first non-CA certificate from server
httpsSite = new URL(HTTPS_URL)
conn = httpsSite.openConnection()
conn.connect()
urlCert = conn.serverCertificates.findAll {
    it.type == 'X.509' && it.basicConstraints == -1
}.first()
conn.disconnect()

// default password for JVM keystores
password = 'changeit'.chars

// add certificate to a keyfile
addCert = {keyFile ->
  keyStore = KeyStore.getInstance('jks')
  keyFile.withInputStream {
  	keyStore.load(it, password)
  }
  found = keyStore.aliases().findAll {
    keyStore.getCertificate(it).hashCode() == urlCert.hashCode()
  }
  if (! found) {
    keyStore.setEntry(httpsSite.host, new KeyStore.TrustedCertificateEntry(urlCert), null)
    keyFile.withOutputStream {
        keyStore.store(it, password)
    }
  }
}

new File(JAVA_ROOT).listFiles().findAll {
  it.directory
}.collect {
  new File(it, 'jre/lib/security/cacerts')
}.findAll {
  it.exists()
}.each {
  addCert(it)
}