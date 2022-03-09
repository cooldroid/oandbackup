#!/system/bin/sh

command=$1
shift

utilbox=$1
shift

archive=-
compress=
exclude=

while [[ $1 == --* ]]; do

  if [[ $1 == --compress ]]; then
    compress="z"
    shift
    continue
  fi

  if [[ $1 == --archive ]]; then
    shift
    archive=$1
    shift
    continue
  fi

  if [[ $1 == --exclude ]]; then
    shift
    exclude="$exclude -X $1"
    shift
    continue
  fi

  break

done

tarcmd="$utilbox tar"
if tar --version | grep -q "GNU tar" ; then
  tarcmd="$(which tar) --ignore-failed-read"
fi

if [[ $command == "create" ]]; then

  dir=$1
  shift

  #cd $dir && (
  #  ($utilbox ls -1A | $utilbox tar -c -f "$archive" $exclude -T -) || (dd if=/dev/zero bs=1024c count=1 2>/dev/null)
  #)
  $tarcmd -c"$compress" -f "$archive" -C "$dir" $exclude .

  exit $?

elif [[ $command == "extract" ]]; then

  dir=$1
  shift

  $tarcmd -x"$compress" -f "$archive" -C "$dir" $exclude

  exit $?
fi

