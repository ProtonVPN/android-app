#! /bin/zsh
set -o shwordsplit

if [ "$ICSCROWDAPIKEY" != "" ]
then
	echo "Generating new translation archives"
	#fetch -q -1 -o - "http://api.crowdin.net/api/project/ics-openvpn/export?key=$ICSCROWDAPIKEY"
    curl "https://api.crowdin.com/api/project/ics-openvpn/export?key=$ICSCROWDAPIKEY"
fi

echo "Fetch translation archive"
wget -nv https://crowdin.com/backend/download/project/ics-openvpn.zip


# Chinese language require zh-CN and zh-TW

typeset -A langhash
langhash=(zh-CN zh-rCN zh-TW zh-rTW id-ID in ca-ES ca cs-CZ cs et-EE et
          ja-JP ja ko-KR ko sv-SE sv uk-UA uk vi-VN vi sl-SI sl da-DK
          da be-BY be he-IL he ar-SA ar fa-IR fa si-LK si pt-BR pt-rBR
          sr-SP sr-rSP el-GR el sk-SK sk)

langtoinclude="de es fr hu it no nl pl pt ro ru tr"

for lang in $langtoinclude ${(k)langhash}
do
    if (( ${+langhash[$lang]} )); then
        alang=$lang
        rlang=${langhash[$lang]}
    else
        alang=$lang-${lang:u}
        rlang=$lang
    fi

    mkdir -p src/ui/res/values-$rlang/
    echo "$alang -> $rlang"
    tar -xv -C src/ui/res/values-$rlang/ --strip-components 2 -f ics-openvpn.zip res/values-$alang/
done

if [ "$NODELETE" = "" ]; then
  rm ics-openvpn.zip
fi