# Winamp YTM — Tam Ekran YouTube Music Android Uygulaması

Bu proje, `https://music.youtube.com` adresini tam ekran (immersive) masaüstü modunda açan, eklediğiniz **Winamp - YouTube Music (v2.95.37)** JavaScript kodunu otomatik çalıştıran ve kodu sonradan uygulama içinden güncellemenize olanak tanıyan eksiksiz bir Android projesidir.

## Özellikler
- **Tam Ekran Masaüstü WebView:** Durum ve gezinme çubuklarını gizler, masaüstü Chrome kullanıcı aracısı (User-Agent) ile YouTube Music'in masaüstü arayüzünü yükler.
- **Gömülü Kullanıcı Betiği:** `app/src/main/assets/userscript.js` içinde gönderdiğiniz kod birebir yer alır ve sayfa açıldığında otomatik enjekte edilir.
- **Uygulama İçi JS Güncelleme:** Sağ üst köşedeki yarı saydam **JS** düğmesine dokunarak:
  1. **Dosyadan JS Yükle:** Telefonunuzdaki yeni bir `.js` veya `.txt` dosyasını seçip anında uygulayabilirsiniz.
  2. **URL'den Güncelle:** GitHub Raw / Gist gibi bir `https://` bağlantısından son sürümü indirip uygulayabilirsiniz (son girilen URL hatırlanır).
  3. **Panodan Yapıştır:** Kopyaladığınız kodu doğrudan kaydedebilirsiniz.
  4. **Varsayılan Koda Sıfırla:** Gömülü `v2.95.37` sürümüne geri dönebilirsiniz.

## APK Nasıl Derlenir?

### Yöntem 1: Android Studio Kurmadan (GitHub Actions ile ~2 dakikada)
1. GitHub'da yeni bir depo (özel/private olabilir) oluşturun ve bu ZIP içindeki tüm dosyaları depoya yükleyin.
2. Depoda **Actions** sekmesine gidin; **Build Android APK** iş akışı otomatik başlar (başlamazsa **Run workflow** düğmesine basın).
3. İşlem tamamlandığında alttaki **Artifacts** bölümünden **WinampYTM-APK** dosyasını indirin ve telefonunuza kurun.

### Yöntem 2: Android Studio ile Yerel Derleme
1. ZIP dosyasını açın ve **Android Studio** içinde **Open** diyerek `WinampYTM-Android` klasörünü seçin.
2. Gradle senkronizasyonu bittikten sonra üst menüden **Build > Build Bundle(s) / APK(s) > Build APK(s)** yolunu izleyin.
3. Oluşan APK `app/build/outputs/apk/debug/app-debug.apk` konumunda hazır olacaktır.
