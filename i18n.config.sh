#!/usr/bin/env bash
set -eo pipefail

##
# Human friendly name we use when we make the call to #localization to inform about the update inside the App
export I18N_APP_PROJECT_NAME="$(head -n 1 README.md | sed 's/\x23 //')"

##
# The script runs this function after the download + extraction
# Purpose: Copy extracted translations into the application
function importNewLocales {

  local outputFile='strings.xml';

  for file in $(ls "$OUTPUT_TRANSLATIONS_DIR"); do
    local output="$I18N_OUTPUT_DIR/values-${file/\.xml/}";

    # When we add a new translation the dir doesn't exist
    if [ ! -d "$output" ]; then
      log "create directory $output";
      mkdir "$output";
    fi

    log "copy $file to $output";
    cp "$OUTPUT_TRANSLATIONS_DIR/$file" "$output/$outputFile";
  done;
}

##
# The script runs this function before making the commit
# Purpose: Copy custom git add
function gitAdd {
  git add "$I18N_OUTPUT_DIR/values-*"
}