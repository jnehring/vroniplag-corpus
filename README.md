# Vroniplag Corpus

From this site you can download the Vroniplag corpus. We performed the following steps in order to get our
final dataset ([Click here to download the data directly](add)): 


### STEP 1 - Crawl the data
Run VroniplagCrawler to get initial fragment table
```
--spring.profiles.active=vroniplag
```
	
Run AnnotationDownloader to get the annotations which are added after javascript was run on the website
```
--spring.profiles.active=annotation-downloader
```

### STEP 2: Language Detection
In this step the fields *lang_source* and *lang_plagiat* of the table *fragment* are filled.
Run the detectLang - method in cleaner.py with a connection to your vroniplag database.
```
conn = MySQLdb.connect(host='127.0.0.1', user='', passwd="", db='vroniplag', charset='utf8')
detectLang(conn)
```
	
### STEP 3 Get positive pp-pairs: 
Run *Application.java* with the AnnotationMatcher profile to get the *annotation* table:
```
--spring.profiles.active=annotation-matcher
```


### STEP 4 Add negative pp-pairs, data cleaning:
Run the Python script *cleaner.py*


## License

The software in this repository is published under Apache 2.0 license.

The data was crawled from [Vroniplag](de.vroniplag.wikia.com) and published under the [Creative Commons Attribution-Share Alike 3.0 Unported](https://creativecommons.org/licenses/by-sa/3.0/deed.en) license.
