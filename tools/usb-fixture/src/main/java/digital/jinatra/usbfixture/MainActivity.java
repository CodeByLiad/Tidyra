package digital.jinatra.usbfixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    TextView text = new TextView(this);
    text.setText("Preparing disposable Tidyra test data...");
    setContentView(text);
    new Thread(() -> {
      try {
        File marker = new File(getFilesDir(), "keep.txt");
        try (FileOutputStream out = new FileOutputStream(marker)) { out.write("TIDYRA_USB_KEEP_V1".getBytes(StandardCharsets.UTF_8)); }
        File external = new File(getExternalFilesDir(null), "keep-external.txt");
        try (FileOutputStream out = new FileOutputStream(external)) { out.write("TIDYRA_USB_EXTERNAL_V1".getBytes(StandardCharsets.UTF_8)); }
        File cache = new File(getCacheDir(), "disposable-cache.dat");
        byte[] bytes = new byte[1024 * 1024];
        new java.util.Random(20261116).nextBytes(bytes);
        try (FileOutputStream out = new FileOutputStream(cache)) {
          for (int i = 0; i < 16; i++) out.write(bytes);
          out.getFD().sync();
        }
        runOnUiThread(() -> text.setText("Tidyra USB Fixture ready.\n16 MiB cache.\nInternal and external keep markers.\nThis app is disposable."));
      } catch (Exception e) { runOnUiThread(() -> text.setText("Fixture error: " + e)); }
    }).start();
  }
}
