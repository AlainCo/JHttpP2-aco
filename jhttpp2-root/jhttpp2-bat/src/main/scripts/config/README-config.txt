When you first start jHTTPp2 on a new machine,
a folder with the machine name is created here.


First, a folder named from the machine domain ended with "-default" name is searched here
-> %USERDOMAIN%-default
if it exists its content is copied

typically when you have made a good configuration for a corporate network, put is in %USERDOMAIN%-default so it will be reused everywhere


if no such folder exists, then the sibling folder of this one ..\config-default is used, end content is copied
 
 
 once created, the folder is reused.