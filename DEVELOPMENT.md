## Get started:

1.  Clone the repository with command `git clone ...`
2.  Change into the toolkit main directory `cd com.ibm.streamsx.mail`
3.  Run `ant all` to build toolkit.

## Develop in Studio:

1.  Clone the repository
2.  Go to com.ibm.streamsx.mail
3.  Run `ant maven-deps` to download the dependencies required by the project
3.  In Streams Studio, Import...
5.  In the dialog, select InfoSphere Streams -> SPL Project
6.  Select com.ibm.streamsx.mail to import the project into studio.

## The top-level build.xml

The top-level build.xml contains the main targets:

* **all** - Builds and creates SPLDOC for the toolkit and samples. Developers should ensure this target is successful when creating a pull request.
* **toolkit** - Build the complete toolkit code
* **samples** - Builds all samples. Developers should ensure this target is successful when creating a pull request.
* **release** - Builds release artifacts, which is a tar bundle containing the toolkits and samples. It includes stamping the SPLDOC and toolkit version numbers with the git commit number (thus requires git to be available).
* **test** - Start the test
* **spldoc** - Generate the toolkit documentation

Execute the comman `ant -p` to display the target information.

## Requirements

* git (version 1.8.3 or later)
* Apache maven (version 3.3.9 or later)
* Apache ant (version 3.3.9 or later)
* IBM Streams (version 4.3 or later)

