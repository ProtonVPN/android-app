if [ "$1" = "args" ]; then
    echo " JSON=1 -- build with JsonCpp library"
elif [ "$1" = "deps" ]; then
    # JsonCpp
    if [ "$JSON" = "1" ]; then
	CPPFLAGS="$CPPFLAGS $(pkg-config --cflags jsoncpp) -DHAVE_JSONCPP"
	EXTRA_SRC_OBJ="$EXTRA_SRC_OBJ $(pkg-config --libs jsoncpp)"
    fi
fi
