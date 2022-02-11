#!/usr/bin/env python
# Quick and dirty script to generate googlecode wiki pages

import codecs
import xml.dom.minidom as dom
import os.path
import re
import HTMLParser

faqpath = "/Users/arne/software/icsopenvpn.ghpages"


template = open("misc/header.html").read()

def getString(strid,lang):
    ostr=""
    if strid in strres[lang]:
        ostr=strres[lang][strid]
    else:
        ostr=strres["default"][strid]

    ostr = ostr.replace("&lt;","<")
    ostr = ostr.replace("&gt;",">")
    ostr = ostr.replace("\\\"","\"")
    ostr = ostr.replace("\\'","'")
    ostr = ostr.replace("\\n","<p>")
    ostr= HTMLParser.HTMLParser().unescape(ostr)
    return ostr

def genPage(javafile, lang):
    #{R.string.faq_howto_title, R.string.faq_howto},
    out =""

    notmatched = None
    for l in javafile:
        
        m = re.search("FAQEntry.*\((.*),(.*), R.string.([a-z0-9_]+),.*R.string.([a-z0-9_]+)\)", l)
        if m:
            if notmatched and notmatched.strip():
                print "Line did not match: %s" % notmatched
                
            notmatched = None
            (ver1, ver2, title, body) = m.groups()
            verHeader = getVerHeader(ver1.strip(), ver2.strip(), lang)

            section = """<h2>
            <a name="%(titleid)s"></a>
            %(title)s
            <a href="#%(titleid)s" class="section_anchor"> </a>
            </h2>
            %(verinfo)s
            %(content)s
            """


            if verHeader:
                verinfo += "<small><i>%s</i></small> <br/>\n" % verHeader
            else:
                verinfo =""

            content = "%s\n" % getString(body,lang)
            if body == "faq_system_dialogs_title":
                content += "%s\n" % getString("faq_system_dialog_xposed",lang)


            c = {'titleid': title,
                 'title': getString(title,lang),
                 'verinfo': verinfo,
                 'content': content
                 }

            out+=section % c


        else:
            notmatched = l


    return  out

def getVerHeader(startVersion, endVersion, lang):
    if startVersion == "Build.VERSION_CODES.ICE_CREAM_SANDWICH":
        if endVersion == "-1":
            return None
        else:
            return getString("version_upto", lang) % getVersionString(endVersion)
    if endVersion == "-1":
        return getString("version_and_later", lang) % getVersionString(startVersion)

    startver = getVersionString(startVersion)

    if endVersion == startVersion:
        return startver
    else:
        return "%s - %s" % (startver, getVersionString(endVersion))
        

def getVersionString(ver):
    if ver == "Build.VERSION_CODES.ICE_CREAM_SANDWICH":
        return "4.0 (Ice Cream Sandwich)"
    elif ver == "-441":
        return "4.4.1 (Kit Kat)"
    elif ver == "-442":
        return "4.4.2 (Kit Kat)"
    elif ver == "Build.VERSION_CODES.JELLY_BEAN_MR2":
        return "4.3 (Jelly Bean MR2)"
    elif ver == "Build.VERSION_CODES.KITKAT":
        return "4.4 (Kit Kat)"
    elif ver == "Build.VERSION_CODES.LOLLIPOP":
        return "5.0 (Lollipop)"
    elif ver == "Build.VERSION_CODES.LOLLIPOP_MR1":
        return "5.1 (Lollipop MR1)"
    else:
        return "API " + ver

            
def genPageXML(faqdom,lang):
    out =""
    
    #out+="#summary %s\n" % getString("faq_summary",lang)
    out+= header

    for xmld in faqdom.firstChild.childNodes:
        for xmle in xmld.childNodes:
            if xmle.nodeName == "TextView":
                style =  xmle.getAttribute("style")

                textstyle = None
                if style == "@style/faqhead":
                    textstyle = "== %s ==\n"
                elif style == "@style/faqitem":
                    textstyle = "%s\n"

                atext = xmle.getAttribute("android:text")
                aid = xmle.getAttribute("android:id")
                if atext:
                    atextid = atext.replace("@string/","")
                else:
                    atextid = aid.replace("@+id/","")

                out += textstyle % getString(atextid,lang)

    return out
            
	
strres={}

def loadstrres(filename,lang):
    xmlstr = dom.parse(filename)
    strres[lang]={}
    for xmld in xmlstr.childNodes:
        for xmle in xmld.childNodes:
            if xmle.nodeName == "string":
                strname= xmle.getAttribute("name")
                strdata = xmle.firstChild.data
                strres[lang][strname]=strdata
    

def main():
    
    loadstrres("src/main/res/values/strings.xml","default")
    
    #faqdom = dom.parse("src/main/res/layout/faq.xml")
    faqdom = open("src/ui/java/de/blinkt/openvpn/fragments/FaqFragment.java").readlines()
    faq= genPage(faqdom,"default")

    open(faqpath + "/FAQ.html","w").write(template % {'content': faq})

    for directory in os.listdir("src/main/res"):
        if directory.startswith("values-") and directory.find("-sw")==-1 and not directory.startswith("values-v"):
            lang = directory.split("-",1)[1]
            print lang
            loadstrres("src/ui/res/values-%s/strings.xml" % lang,lang)

            langdir= "%s/FAQ-%s" %(faqpath,lang)
            if lang=="zh-rCN":
                langdir= "%s/FAQ-%s" %(faqpath,"zh-Hans")
            elif lang=="zh-rTW":
                langdir= "%s/FAQ-%s" %(faqpath,"zh-Hant")

                
            if not os.path.exists(langdir):
                os.mkdir(langdir)

            faq= genPage(faqdom,lang)
            open("%s.html" % langdir,"w").write(faq.encode("utf-8"))

            checkFormatString(lang)

def checkFormatString(lang):
    for strid in strres["default"]:
        ostr = getString(strid,"default")
        tstr = getString(strid,lang)


        for f in ["%s", "%d", "%f"] + ["%%%d$s" % d for d in range(0,10)] + ["%%%d$d" % d for d in range(0,10)]:
            ino = ostr.find(f)==-1
            int = tstr.find(f)==-1

            if ino != int:
                print "Mismatch StringID(%s): " % lang,strid,"Original String:",ostr,"Translated String:",tstr
                
if __name__=="__main__":
    main()
