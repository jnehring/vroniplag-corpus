# *-* coding: utf-8 *-*
from __future__ import unicode_literals
import datetime
from langdetect import detect
import MySQLdb
import pandas as pd
import sys

# USES PYTHON 2!!


def addNegatives(conn, tablename, fakeSrcDict):
    err_count = 0
    nb_negatives = 0
    cur = executeSql("SELECT * from " + tablename + " WHERE isParaphrase=1;")
    for row in cur:
        annotation_id = row['annotation_identifier']
        fake_src = fakeSrcDict[annotation_id]
        if not fake_src:
            continue
        row['annotation_identifier'] = annotation_id + "_f"
        row['source_sent'] = fake_src
        row['isParaphrase'] = 0

        query = u"INSERT INTO " + tablename + " ("
        query = addValToQuery(row.keys(), query)
        query += u" VALUES ("
        values = []
        for item in row.values():
            # print(type(item))
            if type(item) == unicode:
                item = item.replace("'", "\\'")
                item = u"'" + item + u"'"
                item = item.encode("utf8")
                # print(item)
                values.append(item)
            else:
                values.append(str(item))

        query = addValToQuery(values, query)
        query += u";"
        query = query.encode('utf8')
        try:
            cur2 = conn.cursor()
            cur2.execute(query)
            cur2.close()
            nb_negatives += 1
        except Exception as e:
            err_count += 1
            print(e)
            print(row['url'])

    print("#errors: {}".format(err_count))
    print("#nb fake pairs: {}".format(nb_negatives))
    print("")

    conn.commit()


def addValToQuery(vals, query):
    """returns query as unicode
    """
    query = unicode(query)
    for idx, key in enumerate(vals):
        key = key.decode('utf8')
        if idx == len(vals)-1:
            query += key + u") "
        else:
            query += key + u", "

    return query


def createMonolingualLangTable(conn, table, lang, word_ratio_min, word_ratio_max, bow_diff_min):
    query = getQueryMonolingualLangTable(table, lang, wr_min=word_ratio_min, wr_max=word_ratio_max, bd_min=bow_diff_min)
    cur = executeSql(query)
    conn.commit()
    cur.close()

    query = "ALTER TABLE " + table + " ADD COLUMN isParaphrase TINYINT(1) DEFAULT 1"
    cur = executeSql(query)
    conn.commit()
    cur.close()

    ids_to_fake_sources = getFakeSources(lang)
    addNegatives(conn, table, ids_to_fake_sources)


def detectLang(conn):
    # language detection

    #conn = MySQLdb.connect(host='127.0.0.1', user='root', passwd="", db='vroniplag', charset='utf8')
    cur = CONN.cursor(MySQLdb.cursors.DictCursor)

    cur.execute("SELECT * FROM fragment ORDER BY fragment_identifier")

    for idx, r in enumerate(cur):

        try:
            url = r["url"]
            original = r["source_text"]
            plagiat = r["plagiat_text"]
            lang_original = detect(original)
            lang_plagiat = detect(plagiat)

            query = "UPDATE fragment SET lang_source='" + lang_original + "', lang_plagiat='" + lang_plagiat
            query += "' WHERE url='" + url + "';"
            cur2 = conn.cursor()
            cur2.execute(query)
            cur2.close()

            if idx % 100 == 0:
                print("nb processed: {}".format(idx+1))
        except Exception as e:
            sys.stderr.write("Exception:" + url)
            print(e)

    conn.commit()
    cur.close()
    conn.close()


def executeSql(conn, query):
    cur = conn.cursor(MySQLdb.cursors.DictCursor)
    cur.execute(query)
    return cur


def getDataFromMonolingualLangTable(lang, keys, isParaphrase, excludeInnerIdenticals=True):
    query = "SELECT * from monolingual" + lang.upper() + " WHERE isParaphrase=" + str(isParaphrase) + ";"
    cur = executeSql(query)
    rows = []
    counter = 0
    for row in cur:
        # if row['annotation_identifier'] != "Aaf/Fragment 009 01_7":
        #    continue

        new_row = []
        for key in keys:
            val = row[key]
            if type(val) == unicode:
                val = val.encode("utf-8")

            new_row.append(val)
        if excludeInnerIdenticals:
            plag = row['plagiat_sent'].lower()
            src = row['source_sent'].lower()
            if plag in src or src in plag:
                counter += 1
                continue
        rows.append(new_row)

    print("# skipped (inner identicals): " + str(counter))
    return rows


def getFakeSources(lang):
    """Get mapping from annotation_identifier to fake source sent
    """
    query = "SELECT * from monolingual WHERE lang_plagiat='"+lang+"' and lang_source='"+lang+"';"
    cur_annotation = executeSql(query)
    ids_to_fake_sources = {}
    for row in cur_annotation:
        key = row['annotation_identifier']
        ids_to_fake_sources[key] = row['fake_source_sent']

    cur_annotation.close()
    return ids_to_fake_sources


def getQueryMonolingualLangTable(tablename, lang, wr_min, wr_max, bd_min=1):
    query = "CREATE TABLE " + tablename + " as SELECT annotation_identifier, url, plagiat_sent, source_sent, "
    query += "bow_diff, nb_words_ratio "
    query += "from monolingual where lang_plagiat='" + lang + "' and lang_source='" + lang + "' and "
    query += "nb_words_ratio>" + str(wr_min) + " and nb_words_ratio<" + str(wr_max) + " and "
    query += "bow_diff>" + str(bd_min)
    query += ";"
    print(query)
    print("")
    return query


def splitData(percent_test, data):
    split = int(percent_test * len(data))
    test_data = data[:split+1]
    train_data = data[split+1:]
    return train_data, test_data


def write_csv_files(lang, suffix, keys):
    data_isPP = getDataFromMonolingualLangTable(lang, keys, 1)
    print("nb paraphrases: " + str(len(data_isPP)))
    data_noPP = getDataFromMonolingualLangTable(lang, keys, 0)
    print("nb fake-paraphrases: " + str(len(data_noPP)))

    train_data_isPP, test_data_isPP = splitData(0.2, data_isPP)
    train_data_noPP, test_data_noPP = splitData(0.2, data_noPP)

    train_data = pd.DataFrame(train_data_isPP + train_data_noPP, columns=keys)
    test_data = pd.DataFrame(test_data_isPP + test_data_noPP, columns=keys)

    print("train shape: " + str(train_data.shape))
    print("test shape: " + str(test_data.shape))
    train_file = lang + suffix + '_train.csv'
    test_file = lang + suffix + '_test.csv'
    train_data.to_csv(lang + suffix + '_train.csv', encoding='utf-8')
    test_data.to_csv(lang + suffix + '_test.csv', encoding='utf-8')

    print("train file for lang {} written to {}.".format(lang, train_file))
    print("test file for lang {} written to {}.".format(lang, test_file))


if __name__ == '__main__':
    # CRAWL THE DATA
    # STEP 0: Run VroniplagCrawler to get initial fragment table
    # STEP 1: Run AnnotationDownloader to get the annotations which are added after javascript was run on website
    # (for future: integrate STEP 0+1)

    # STEP 2: Language Detection (fills lang_source + lang_plagiat columns of fragment table)
    conn = MySQLdb.connect(host='127.0.0.1', user='root', passwd="", db='vroniplag', charset='utf8')
    #detectLang(conn)

    # STEP 3: Run Java AnnotationMatcher to get annotation table
#     try:
#         cur = executeSql("SELECT * from annotation")
#     except:
#         sys.stderr.write("Run Java AnnotationMatcher first")
#         sys.exit()

    # STEP 4: create monolingual table
    query = "CREATE TABLE monolingual AS SELECT annotation_identifier, url, "
    query += "plagiat_sent, source_sent, fake_source_sent, lang_plagiat, lang_source, "
    query += "bow_diff, nb_words_ratio "
    query += "from annotation "
    query += "WHERE type='Verschleierung' and lang_plagiat=lang_source"

    cur = executeSql(conn, query)
    conn.commit()
    cur.close()

    # DATA CLEANING
    # STEP 5: create monolingual tables for each language
    #         - add negative pairs
    #         - apply constraints (word-ratio etc.)
    word_ratio_min = 0.5
    word_ratio_max = 1.5
    bow_diff_min = 6

    createMonolingualLangTable(conn, "monolingualEN", "en", word_ratio_min, word_ratio_max, bow_diff_min)
    createMonolingualLangTable(conn, "monolingualDE", "de", word_ratio_min, word_ratio_max, bow_diff_min)
    createMonolingualLangTable(conn, "monolingualES", "es", word_ratio_min, word_ratio_max, bow_diff_min)

    # STEP 6: WRITE TRAIN and TEST FILES for each language to csv files
    now = datetime.datetime.now()
    today = str(now.year) + "-" + str(now.month) + "-" + str(now.day)
    keys = ['plagiat_sent', 'source_sent', 'isParaphrase', 'url']
    write_csv_files("en", today, keys)
    write_csv_files("de", today, keys)
    write_csv_files("es", today, keys)

    conn.close()
