# Changes

## v2.0.1
* Globalization support

## v2.0.0
* New global build script with new targets: spldoc, test, release
* Update of used lib javax.mail to 1.6.2
* Operator SendMail: removed parameters: batch, hostname, hostport, authentication
* Operator SendMail: new parameters: smtpHost, smtpPort, toAttribute, ccAttribute, bccAttribute, encryptionType, acceptAllCertificates, enableOperatorLog
* Operator SendMail: removed threading support
* Operator SendMail: new optional error output port
* Operator SendMail: new support to generate operator logs in case of e-mail failures
* Operator ReadMail: removed parameters: hostname, hostport
* Operator ReadMail: new parameters: imapHost, imapPort, initDelay, iterations, readNewMailsOnly, deleteAfterRead, encryptionType, acceptAllCertificates, enableOperatorLog
* Operator ReadMail: new optional error output port
* Operator ReadMail: new optional error output port on port 0
* Support for message id
* Samples added
* Automated tests


## v1.0.1:
* Initial release

