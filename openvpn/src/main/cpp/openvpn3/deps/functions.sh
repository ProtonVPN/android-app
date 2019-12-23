function check_download()
{
  if [ -f $DL/$FNAME ]; then
    CHECK=$(shasum -a 256 $DL/$FNAME |awk '{printf $1};')
    if [ "$CHECK" == "$CSUM" ]; then
      return 0
    else
      echo "Checksum mismatch for $FNAME. Was $CHECK, expected $CSUM"
    fi
  else
    echo "$FNAME not found."
  fi

  return -1
}

function download()
{
  check_download && return 0

  rm -f $DL/$FNAME
  if [ -n "$URL" ]; then
    wget $URL -O $DL/$FNAME
  else
    echo URL must be specified
    exit 1
  fi

  check_download || return -1
}

function apply_patches()
{
  DEP_NAME=$1

  # change directory since git apply got confused when
  # applying patches to files which are not found in index
  DIR=$(pwd)
  pushd ${DIR}
  cd /tmp
  # apply pre-generated patches
  for file in $O3/core/deps/${DEP_NAME}/patches/*.patch; do
    echo Applying patch: $file
    git apply --directory ${DIR} --unsafe-path $file
  done
  popd
}
