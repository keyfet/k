# MiniTM – Android betik tarayıcısı

Android 10 ve üstünde çalışan, içinde kullanıcı tarafından seçilen `.user.js`, `.js`, `.txt` betiklerini açılan web sayfasında otomatik çalıştıran küçük WebView tarayıcı projesi.

## Özellikler

- Site adresi, geri/ileri, yenileme, mobil/masaüstü kullanıcı aracısı
- Betik ekleme, kodu yapıştırma, dosyadan içe aktarma, düzenleme, etkinleştirme ve silme
- `@match`, `@include`, `@exclude`, `@exclude-match`, `@noframes`, `@run-at`
- Document-start desteği bulunan Android WebView sürümlerinde sayfa başında çalıştırma
- Sınırlı GM API: `GM_getValue`, `GM_setValue`, `GM_deleteValue`, `GM_listValues`, `GM_addStyle`, `GM_registerMenuCommand`, `GM_openInTab`, `GM_download`, `GM_setClipboard`, `GM_log`, `GM_info`, `unsafeWindow` ve CORS sınırlı `GM_xmlhttpRequest`
- Yerel betik saklama, Android Paylaş menüsünden URL açma

## APK nasıl alınır?

**Android Studio (en basit kaynak derlemesi):** ZIP dosyasını açın; Android Studio → Open → `MiniTM_Android` klasörü → Gradle eşitlemesi → Build → Build APK(s). Çıktı `app/build/outputs/apk/debug/app-debug.apk`.

**GitHub Actions (Android Studio gerektirmez):** ZIP içeriğini kendi boş GitHub deponuza yükleyin. Actions → `Android APK oluştur` → Run workflow. İşlem başarılı olduktan sonra Artifacts bölümünden `MiniTM-debug-APK` dosyasını indirin ve içindeki `.apk` dosyasını Android'e kurun. GitHub Actions internet erişimi ve ücretsiz hesap gerektirebilir.

> Bu ZIP **APK değildir**. Kaynak kodu ve APK üretim iş akışını içerir.

## Kullanım

1. Uygulamayı açın ve sağ üstteki **JS** düğmesine basın.
2. **İçe aktar** ile `.user.js`, `.js` veya `.txt` seçin; ya da **Yeni** ile Tampermonkey kodunu yapıştırın.
3. Betiğin başındaki `@match` adresiyle eşleşen web sayfasına gidin. Değişiklik sonrası sayfa yeniden yüklenir.
4. Test: `Ornek_Betik.user.js` dosyasını içe aktarın ve `https://example.com/` sayfasını açın. Köşede "MiniTM çalışıyor" çıkmalı.

## Sınırlar

Bu uygulama **Tampermonkey'in kendisi değildir** ve tüm Tampermonkey özelliklerini sunmaz. Özellikle:

- `@require` ile bağımlılık indirme ve `@resource` desteği **yoktur**.
- `GM_xmlhttpRequest`, tarayıcının CORS kurallarını aşamaz.
- GM depolaması uygulamanın **her site kökeninde** ayrıdır, Tampermonkey uzantısı gibi siteler arası ortak değildir.
- `GM_openInTab` ve bazı oturum açma/DRM/yeni pencere akışları Android WebView'de sınırlı olabilir.
- Çok uzun betikler, `import` modülleri ve bazı dinamik web siteleri uyumsuz olabilir.
- Kullanıcı komutları ve sayfa içi kod çalıştırma test için tasarlanmıştır. Üretim ortamında güvenilir betikler kullanın.

**En geniş gerçek Tampermonkey uyumluluğu:** Android Firefox + resmi Tampermonkey eklentisi: https://addons.mozilla.org/tr/android/addon/tampermonkey/ .

## Gizlilik

Kodlar ve ayarlar yalnızca uygulamada tutulur; uygulama kodları kendiliğinden bir sunucuya göndermez. Çalıştırdığınız kullanıcı betikleri, eriştikleri sitelerin içeriğini okuyabilir ve kendi kodlarına göre ağ isteği gönderebilir. Güvenmediğiniz betikleri çalıştırmayın.
