# README --  FrameworkTests

This directory provides an automatic test for the mail toolkit.

## Test Execution

To start the full test execute:  
`./runTest.sh`

To start a quick test, execute:  
`./runTest.sh --category quick`

This script installs the test framework in directory `scripts` and starts the test execution. The script delivers the following result codes:  
0     : all tests Success  
20    : at least one test fails  
25    : at least one test error  
26    : Error during suite execution  
130   : SIGINT received  
other : another fatal error has occurred  

More options are available and explained with command:  
`./runTest.sh --help`

## Test Sequence

The `runTest.sh` installs the test framework into directory `scripts` and starts the test framework. The test framework 
checks if there is a running Streams instance. 

If the Streams instance is not running, a domain and an instance is created from the scratch and started. You can force the 
creation of instance and domain with command line option `--clean`

The location of the mail toolkit to test and the mail-server to use is controlled with properties. The location of standard properties file is 
`tests/TestProperties.sh`.
The path to the mail toolkit under test is defined with the variable `TTPR_streamsxMailToolkit`
The path to the mail toolkit samples is defined with the variable `TTRO_streamsxMailSamplesPath`
The standard properties file expects the mail toolkit in directory `../../com.ibm.streamsx.mail/` and it must be built with the current Streams version. 
The mail toolkit samples are expected in `../../samples`. 

Use command line option `-D <name>=<value>` to set external variables or provide a new properties file with command line option 
`--properties <filename>`.

## Requirements

The test framework requires an valid Streams installation and environment and a running mail-server.

## Installation of a Test E-mail Server

### Install the required Packages

* Install packages `postfix` and `dovecot`

### Postfix Configuration 

* In directory `/etc/postfix` edit the file `main.cf` as root

* Enter the mailserver hostname
```
myhostname = <mailserver fqdn>
```

* Enter the mail domain
```
mydomain = <domain part of mailserver fqdn>
```

* Set the default origin
```
myorigin = $mydomain
```

* Set the interface configuration
```
inet_interfaces = all
#inet_interfaces = localhost
```

* Set the maildir
```
home_mailbox = Maildir/
mailbox_command =
```

* Enter the location for ssl certificate and key file
```
smtpd_tls_cert_file = /etc/pki/postfix/certs/dovecot.pem
smtpd_tls_key_file = /etc/pki/postfix/private/dovecot.pem
smtpd_use_tls=yes
```

* Copy the installed certificates and change the ownership to postfix
```
#cp -p /etc/pki/dovecot/certs/dovecot.pem /etc/pki/postfix/certs/dovecot.pem
#cp -p /etc/pki/dovecot/private/dovecot.pem /etc/pki/postfix/private/dovecot.pem
#chown postfix:postfix /etc/pki/postfix/certs/dovecot.pem
#chown postfix:postfix /etc/pki/postfix/private/dovecot.pem
```
Make sure that the private key file has 0600 access permissions.

* Edit the file `/etc/postfix/master.cf`: In section smtps uncomment the keys 
`syslog_name, smtpd_tls_wrappermode, smtpd_reject_unlisted_recipient, milter_macro_daemon_name`

* The main.cf diff looks like:
```
# diff main.cf main.cf.orig 
77d76
< myhostname = strvm2.net1
85d83
< mydomain = net1
101c99
< myorigin = $mydomain
---
> #myorigin = $mydomain
115c113
< inet_interfaces = all
---
> #inet_interfaces = all
118c116
< #inet_interfaces = localhost
---
> inet_interfaces = localhost
421c419
< home_mailbox = Maildir/
---
> #home_mailbox = Maildir/
451d448
< mailbox_command = 
683,687d679
< 
< smtpd_tls_cert_file = /etc/pki/postfix/certs/dovecot.pem
< smtpd_tls_key_file = /etc/pki/postfix/private/dovecot.pem
< smtpd_use_tls=yes
< 
```

* The master.cf diff looks like:
```
# diff master.cf master.cf.orig 
26,28c26,28
< smtps     inet  n       -       n       -       -       smtpd
<   -o syslog_name=postfix/smtps
<   -o smtpd_tls_wrappermode=yes
---
> #smtps     inet  n       -       n       -       -       smtpd
> #  -o syslog_name=postfix/smtps
> #  -o smtpd_tls_wrappermode=yes
30c30
<   -o smtpd_reject_unlisted_recipient=no
---
> #  -o smtpd_reject_unlisted_recipient=no
35c35
<   -o milter_macro_daemon_name=ORIGINATING
---
> #  -o milter_macro_daemon_name=ORIGINATING
```

### Dovecut Configuration 

* Create 2 new mail users and enter the default password `streams1`
```
#useradd -m mailuser1 -c 'User for Mail'
#useradd -m mailuser2 -c 'User for Mail'
passwd mailuser1
passwd mailuser2
```

* Change file `/etc/dovecot/conf.d/10-master.conf`
```
service auth {
  # Postfix smtp-auth
  unix_listener /var/spool/postfix/private/auth {
    mode = 0660
  }
}
```

* Change file `/etc/dovecot/conf.d/10-mail.conf`
```
first_valid_uid = 500
```

* Change file `/etc/dovecot/conf.d/10-ssl.conf` if necessary
```
first_valid_uid = 500
```

* Change file `/etc/dovecot/conf.d/10-auth.conf`
```
disable_plaintext_auth = no

ssl_cert = </etc/pki/dovecot/certs/dovecot.pem
ssl_key = </etc/pki/dovecot/private/dovecot.pem
```

### Server startup 

* Enable and restart the postfix daemon
```
systemctl enable postfix
systemctl stop postfix
systemctl start postfix
```

* Start and enable the dovecot daemon
```
systemctl enable dovecot
systemctl stop dovecot
systemctl start dovecot
```

* Check the server logs (postfix and dovecot) in `/var/log/maillog`

## Curl

The tests scripts require an installation of a recent version of the curl command.
The `TestSuite.sh` assumes that an anaconda3 version is installed an that the command
`python3` points to the anaconda directory. The test script takes the curl tool from 
the anaconda directory (v7.49)
If this is not applicable, you can override this behavior an pass the command line option 
`-D TTPR_curlCommand=<path to your curl command>`
 to the script test framework.
 
Linkks for curl download:
https://curl.haxx.se/download.html
