package digital.jinatra.usbfixture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Disposable started service; no binding, sticky restart, file edits, or network work. */
public class FixtureService extends Service {
  @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
  @Override public IBinder onBind(Intent intent) { return null; }
}
