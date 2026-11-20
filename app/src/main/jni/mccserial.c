// Opens a tty in raw mode at a fixed baud rate and returns the file descriptor (or -errno).
#include <jni.h>
#include <fcntl.h>
#include <termios.h>
#include <unistd.h>
#include <errno.h>

static speed_t to_speed(int baud) {
    switch (baud) {
        case 4800: return B4800;
        case 9600: return B9600;
        case 115200: return B115200;
        default: return 0;
    }
}

JNIEXPORT jint JNICALL
Java_mcc_app_SerialPort_nativeOpen(JNIEnv *env, jclass clazz, jstring jpath, jint baud) {
    speed_t speed = to_speed(baud);
    if (!speed) return -EINVAL;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    int fd = open(path, O_RDWR | O_NOCTTY);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    if (fd < 0) return -errno;

    struct termios t;
    if (tcgetattr(fd, &t) != 0) { int e = errno; close(fd); return -e; }
    cfmakeraw(&t);                 // 8N1, no echo, no translation
    t.c_cflag |= (CLOCAL | CREAD);
    t.c_cflag &= ~CSTOPB;
    t.c_cc[VMIN] = 0;              // read() returns after VTIME even with no data
    t.c_cc[VTIME] = 2;             // 200 ms
    cfsetispeed(&t, speed);
    cfsetospeed(&t, speed);
    if (tcsetattr(fd, TCSANOW, &t) != 0) { int e = errno; close(fd); return -e; }
    tcflush(fd, TCIOFLUSH);
    return fd;
}
