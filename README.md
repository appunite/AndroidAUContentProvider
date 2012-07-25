# Run example

run this command:

    git clone <repository-url> AndroidAUContentProvider
    
in eclipse:
 * File -> New -> Android Project from Existing Code
 * select AndroidAUContentProvider directory
 * Finish
 * build and run AUContentProviderExample

in ant:

   	cd AUContentProviderExample
   	ant debug
   	ant installd

# Embeding in your project

run this command:

   git submodule add <repository-url> AndroidAUContentProvider

in eclipse:
 * File -> New -> Android Project from Existing Code
 * select AndroidAUContentProvider directory
 * Finish
 * Your project -> properties -> Android
 * Library -> Add..
 * select AUContentProvider and OK

in ant:

 * XXX should be sequent number or 1 if first
 * add to your project project.properties file something like: "android.library.reference.XXX=../AndroidAUContentProvider/AUContentProvider"
 * run those commands:
 
		ant debug
		ant installd


