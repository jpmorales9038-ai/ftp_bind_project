# Shared helpers; source only after MODDIR is set. No system-wide tuning.
umask 077
export LC_ALL=C
mkdir -p "$MODDIR/config"
chmod 700 "$MODDIR/config" 2>/dev/null

enter_global_namespace() {
    _self_ns=$(readlink /proc/self/ns/mnt 2>/dev/null)
    _init_ns=$(readlink /proc/1/ns/mnt 2>/dev/null)
    [ -n "$_self_ns" ] && [ -n "$_init_ns" ] || { echo "ERROR: namespace no disponible"; exit 1; }
    [ "$_self_ns" = "$_init_ns" ] && return 0
    [ "${RCB_NS_ENTERED:-0}" = 0 ] || { echo "ERROR: nsenter no cambio el namespace"; exit 1; }
    command -v nsenter >/dev/null 2>&1 || { echo "ERROR: falta nsenter"; exit 1; }
    export RCB_NS_ENTERED=1
    exec nsenter -t 1 -m -- sh "$SELF" "$@"
}

# Atomic mkdir lock. Fail closed on stale locks; service.sh clears these at boot.
acquire_operation_lock() {
    OP_LOCK="$MODDIR/operation.lock"
    mkdir "$OP_LOCK" 2>/dev/null || { echo "ERROR: otra operacion en curso (o candado obsoleto; reinicia)"; exit 1; }
    echo "$$" > "$OP_LOCK/pid"
    trap 'rm -rf "$OP_LOCK"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM HUP
}

# Match /proc/mounts fields literally; paths with spaces are kernel-escaped.
is_mount_at() {
    _mp=$(readlink -f "$1" 2>/dev/null) || return 1
    _mp=$(printf '%s' "$_mp" | sed 's/ /\\040/g')
    RCB_MOUNT_QUERY="$_mp" awk '$2==ENVIRON["RCB_MOUNT_QUERY"] {found=1} END {exit !found}' /proc/mounts
}

valid_target() {
    case "$1" in
        /sdcard/*|/storage/emulated/0/*|/storage/self/primary/*|/data/media/0/*) ;;
        *) return 1 ;;
    esac
    case "$1" in *'"'*|*'\'*|*'//'*) return 1 ;; esac
    _safe=$(printf '%s' "$1" | tr -d '[:cntrl:]')
    [ "$_safe" = "$1" ] || return 1
    case "/${1#/}/" in */../*|*/./*) return 1 ;; esac
    return 0
}

# Kill child processes before the parent, without broad pkill -f matches.
stop_children() {
    for _child in $(cat "/proc/$1/task/$1/children" 2>/dev/null); do
        stop_children "$_child"
        kill -TERM "$_child" 2>/dev/null
    done
    return 0
}
run_timeout() {
    _limit=$1; shift
    "$@" &
    _cmd=$!
    ( sleep "$_limit"; stop_children "$_cmd"; kill -TERM "$_cmd" 2>/dev/null ) &
    _timer=$!
    wait "$_cmd"; _result=$?
    stop_children "$_timer"
    kill "$_timer" 2>/dev/null
    wait "$_timer" 2>/dev/null
    [ "$_result" -eq 143 ] && return 124
    return "$_result"
}

# Authenticated, loopback-only RC. A fixed port conflict fails the mount;
# it must never silently attach to an unrelated rclone instance.
setup_rc() {
    RC_SECRET="$MODDIR/config/rc_secret"
    if [ ! -s "$RC_SECRET" ]; then
        od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$RC_SECRET.tmp" || return 1
        [ "$(wc -c < "$RC_SECRET.tmp")" -eq 64 ] || return 1
        chmod 600 "$RC_SECRET.tmp" && mv "$RC_SECRET.tmp" "$RC_SECRET" || return 1
    fi
    export RCLONE_RC_USER=rclonebind
    export RCLONE_RC_PASS="$(cat "$RC_SECRET")"
    export RCLONE_RC_ADDR=127.0.0.1:55783
    [ -n "$RCLONE_RC_PASS" ]
}
rc_call() {
    run_timeout 10 "$MODDIR/bin/rclone" rc "$@" \
        --url http://127.0.0.1:55783 --user "$RCLONE_RC_USER" --pass "$RCLONE_RC_PASS" \
        --contimeout 3s --timeout 5s
}
atomic_unmounted() {
    printf '{"mounted":false}\n' > "$MODDIR/status.json.tmp" && mv "$MODDIR/status.json.tmp" "$MODDIR/status.json"
}
