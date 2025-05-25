an example config for a corporate network with 2 proxy.

the proxy defined in properties 
server.http-proxy-prev.* proxy 
is tested before the usual one defined in properties
server.http-proxy.*

if the -prev proxy answers correctly to /favicon.ico (200 or 404) the it is used, else we prefer the usual one

A file named
<prevProxyHost>;<prevProxyPort>.proxy-access.db (eg: whitelist-proxy.corp.com;8080.proxy-access.db )
is created with the list of results of the test (eg: https://www.google.com:443=false)
It prevents reasking at every restart of the proxy, and allows to mark manually site on which to force or avoid the -prex proxy.




It runs under x86 installed JRE.