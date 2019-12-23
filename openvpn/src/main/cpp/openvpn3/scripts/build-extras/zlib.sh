if [ "$1" = "args" ]; then
    echo " ZLIB=1 -- link with zlib"
elif [ "$1" = "deps" ]; then
    if [ "$ZLIB" = "1" ]; then
	LIBS="$LIBS -lz"
	CPPFLAGS="$CPPFLAGS -DHAVE_ZLIB"
    fi
fi
