package com.mycompany.edtstech;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automation Klik Indomaret (tanpa login) menggunakan Selenium WebDriver murni.
 *
 * Alur:
 *  1. Akses https://www.klikindomaret.com
 *  2. Pilih meta kategori "Makanan"
 *  3. Pilih kategori "Madu"
 *  4. Pilih salah satu produk Madu
 *  5. Masuk ke halaman detail produk
 *  6. Klik tombol "+ Keranjang"
 *
 * Opsi runtime (system property):
 *  -Dheadless=true        jalankan Chrome tanpa tampilan (default: false)
 *  -Dproduct.index=0      urutan produk di daftar yang dipilih, mulai dari 0 (default: 0)
 *  -Dpause.seconds=5      jeda di akhir agar hasil bisa dilihat sebelum browser ditutup (default: 5)
 */
public class KlikIndomaretAddToCart {

    // ===================================================================================
    // KONFIGURASI
    // ===================================================================================
    private static final String BASE_URL = "https://www.klikindomaret.com";
    private static final String META_CATEGORY = "Makanan";
    private static final String CATEGORY = "Madu";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(4);

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";

    // ===================================================================================
    // LOCATOR - ubah di sini jika struktur halaman berubah
    // ===================================================================================

    /** Link produk pada halaman daftar produk. */
    private static final By PRODUCT_LINKS = By.cssSelector("a[href*='/product/']");

    /** Judul produk pada halaman detail. */
    private static final By PRODUCT_TITLE = By.cssSelector("h1");

    /** Tombol "+ Keranjang" (harus mengandung tanda '+' agar tidak tertukar dengan ikon keranjang di header). */
    private static final By ADD_TO_CART_BUTTON = By.xpath(
            "//*[self::button or @role='button']"
                    + "[contains(normalize-space(.), '+') and contains(normalize-space(.), 'Keranjang')]");

    /** Indikator sukses (toast/notifikasi) setelah klik "+ Keranjang". */
    private static final By SUCCESS_INDICATOR = By.xpath(
            "//*[text()[contains(translate(., '" + UPPER + "', '" + LOWER + "'), 'berhasil')"
                    + " or contains(translate(., '" + UPPER + "', '" + LOWER + "'), 'ditambahkan ke keranjang')]]");

    /** Label tombol penutup popup yang umum muncul (dicoba secara best-effort). */
    private static final List<String> POPUP_CLOSE_LABELS = List.of("Tutup", "Nanti saja", "Close", "Tolak");

    // ===================================================================================
    // STATE
    // ===================================================================================
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final Actions actions;
    private final JavascriptExecutor js;
    private final Path screenshotDir = Paths.get("screenshots");
    private final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    public KlikIndomaretAddToCart() {
        this.driver = createDriver();
        this.wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
        this.actions = new Actions(driver);
        this.js = (JavascriptExecutor) driver;
    }

    // ===================================================================================
    // ENTRY POINT
    // ===================================================================================
    public static void main(String[] args) throws InterruptedException {
        KlikIndomaretAddToCart test = new KlikIndomaretAddToCart();
        try {
            test.run();
            Thread.sleep(Integer.getInteger("pause.seconds", 5) * 1000L);
        } finally {
            test.close();
        }
    }

    public void run() {
        try {
            step1OpenHomePage();
            step2SelectMetaCategory();
            step3SelectCategory();
            step4SelectProduct();
            step5VerifyProductDetailPage();
            step6ClickAddToCart();
            log("SELESAI: seluruh langkah dijalankan.");
        } catch (RuntimeException e) {
            log("GAGAL: " + e.getMessage());
            screenshot("ERROR");
            throw e;
        }
    }

    // ===================================================================================
    // LANGKAH-LANGKAH TEST
    // ===================================================================================

    /** Langkah 1 - Akses https://www.klikindomaret.com */
    private void step1OpenHomePage() {
        log("Langkah 1: membuka " + BASE_URL);
        driver.get(BASE_URL);
        waitForPageLoad();
        dismissPopups();

        String title = driver.getTitle() == null ? "" : driver.getTitle();
        log("Judul halaman: " + title);
        if (!title.toLowerCase(Locale.ROOT).contains("indomaret")) {
            throw new IllegalStateException(
                    "Halaman beranda tidak sesuai (judul: '" + title + "'). "
                            + "Kemungkinan diblokir bot detection - lihat bagian Troubleshooting di README.");
        }
        screenshot("01-home");
    }

    /** Langkah 2 - Pilih meta kategori "Makanan" */
    private void step2SelectMetaCategory() {
        log("Langkah 2: memilih meta kategori '" + META_CATEGORY + "'");

        // Sebagian layout menyembunyikan meta kategori di balik menu "Kategori".
        WebElement kategoriMenu = findVisibleOrNull(exactText("Kategori"), SHORT_TIMEOUT);
        if (kategoriMenu != null) {
            hover(kategoriMenu);
        }

        WebElement meta = findVisibleOrNull(exactText(META_CATEGORY), SHORT_TIMEOUT);
        if (meta == null && kategoriMenu != null) {
            // Menu tidak terbuka lewat hover -> coba lewat klik.
            clickElement(kategoriMenu);
            meta = waitVisible(exactText(META_CATEGORY), "meta kategori " + META_CATEGORY);
        } else if (meta == null) {
            meta = waitVisible(exactText(META_CATEGORY), "meta kategori " + META_CATEGORY);
        }

        hover(meta); // memunculkan submenu kategori (jika berupa mega menu)
        screenshot("02-meta-kategori");
    }

    /** Langkah 3 - Pilih kategori "Madu" */
    private void step3SelectCategory() {
        log("Langkah 3: memilih kategori '" + CATEGORY + "'");

        WebElement category = findVisibleOrNull(exactText(CATEGORY), SHORT_TIMEOUT);
        if (category == null) {
            // Fallback: submenu tidak tampil lewat hover -> masuk ke halaman meta kategori dulu.
            log("Submenu tidak terlihat via hover, klik meta kategori '" + META_CATEGORY + "'");
            click(exactText(META_CATEGORY), "meta kategori " + META_CATEGORY);
            waitForPageLoad();
            category = waitVisible(exactText(CATEGORY), "kategori " + CATEGORY);
        }
        clickElement(category);

        wait.until(d -> !d.findElements(PRODUCT_LINKS).isEmpty());
        log("Halaman kategori: " + driver.getCurrentUrl());
        screenshot("03-kategori-madu");
    }

    /** Langkah 4 - Pilih salah satu produk Madu */
    private void step4SelectProduct() {
        int requested = Integer.getInteger("product.index", 0);
        Map<String, WebElement> products = collectProducts();
        if (products.isEmpty()) {
            throw new NoSuchElementException("Tidak ada produk yang tampil pada kategori " + CATEGORY);
        }

        int index = Math.max(0, Math.min(requested, products.size() - 1));
        if (index != requested) {
            log("product.index=" + requested + " di luar jangkauan, memakai index " + index);
        }

        List<Map.Entry<String, WebElement>> entries = new ArrayList<>(products.entrySet());
        Map.Entry<String, WebElement> chosen = entries.get(index);
        log("Langkah 4: memilih produk #" + index + " dari " + products.size() + " produk -> " + chosen.getKey());

        clickElement(chosen.getValue());
    }

    /** Langkah 5 - Pastikan sudah berada di halaman detail produk */
    private void step5VerifyProductDetailPage() {
        wait.until(ExpectedConditions.urlContains("/product/"));
        waitForPageLoad();
        dismissPopups();

        WebElement title = waitVisible(PRODUCT_TITLE, "judul produk");
        log("Langkah 5: halaman detail produk -> '" + title.getText() + "'");
        log("URL: " + driver.getCurrentUrl());
        screenshot("05-detail-produk");
    }

    /** Langkah 6 - Klik tombol "+ Keranjang" */
    private void step6ClickAddToCart() {
        log("Langkah 6: klik tombol '+ Keranjang'");
        click(ADD_TO_CART_BUTTON, "tombol + Keranjang");

        WebElement success = findVisibleOrNull(SUCCESS_INDICATOR, Duration.ofSeconds(8));
        screenshot("06-setelah-klik-keranjang");
        if (success != null) {
            log("Konfirmasi dari situs: " + success.getText().replace("\n", " "));
        } else {
            log("PERINGATAN: tombol sudah diklik, tetapi notifikasi sukses tidak terdeteksi. "
                    + "Cek screenshot (bisa jadi situs meminta pilih lokasi/toko atau teks notifikasi berbeda).");
        }
    }

    // ===================================================================================
    // SETUP / TEARDOWN
    // ===================================================================================
    private static WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--start-maximized",
                "--disable-notifications",
                "--disable-popup-blocking",
                "--lang=id-ID",
                "--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        if (Boolean.getBoolean("headless")) {
            options.addArguments("--headless=new", "--window-size=1920,1080");
        }

        // Selenium Manager (bawaan Selenium 4.6+) mengunduh chromedriver yang cocok secara otomatis.
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        return driver;
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
    }

    // ===================================================================================
    // HELPER
    // ===================================================================================

    /** Locator berbasis teks yang terlihat: exact match, tidak case-sensitive, spasi dinormalisasi. */
    private static By exactText(String text) {
        return By.xpath("//*[self::a or self::button or self::li or self::span or self::p or self::div]"
                + "[translate(normalize-space(.), '" + UPPER + "', '" + LOWER + "') = '"
                + text.toLowerCase(Locale.ROOT) + "']");
    }

    private static WebElement firstDisplayed(SearchContext context, By by) {
        for (WebElement element : context.findElements(by)) {
            try {
                if (element.isDisplayed()) {
                    return element;
                }
            } catch (StaleElementReferenceException ignored) {
                // elemen tergantikan oleh re-render SPA, lanjut ke kandidat berikutnya
            }
        }
        return null;
    }

    private WebElement waitVisible(By by, String description) {
        try {
            return wait.until(d -> firstDisplayed(d, by));
        } catch (TimeoutException e) {
            throw new NoSuchElementException("Elemen tidak ditemukan/terlihat: " + description + " [" + by + "]", e);
        }
    }

    private WebElement findVisibleOrNull(By by, Duration timeout) {
        try {
            return new WebDriverWait(driver, timeout).until(d -> firstDisplayed(d, by));
        } catch (TimeoutException e) {
            return null;
        }
    }

    /** Klik elemen berdasarkan locator, dengan retry bila elemen stale akibat re-render. */
    private void click(By by, String description) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebElement element = waitVisible(by, description);
            try {
                clickElement(element);
                return;
            } catch (StaleElementReferenceException e) {
                log("Elemen '" + description + "' stale, mengulang (" + attempt + "/3)");
            }
        }
        throw new IllegalStateException("Gagal mengklik: " + description);
    }

    /** Scroll ke elemen lalu klik; jika terhalang overlay, fallback ke JavaScript click. */
    private void clickElement(WebElement element) {
        scrollIntoView(element);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (ElementClickInterceptedException | TimeoutException e) {
            log("Klik biasa terhalang, memakai JavaScript click");
            js.executeScript("arguments[0].click();", element);
        }
    }

    private void hover(WebElement element) {
        scrollIntoView(element);
        actions.moveToElement(element).pause(Duration.ofMillis(300)).perform();
    }

    private void scrollIntoView(WebElement element) {
        js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", element);
    }

    private void waitForPageLoad() {
        wait.until(d -> "complete".equals(js.executeScript("return document.readyState")));
    }

    /** Menutup popup/banner yang umum muncul. Best-effort: tidak melempar error jika tidak ada. */
    private void dismissPopups() {
        for (String label : POPUP_CLOSE_LABELS) {
            try {
                WebElement button = firstDisplayed(driver, exactText(label));
                if (button != null) {
                    log("Menutup popup: '" + label + "'");
                    clickElement(button);
                }
            } catch (WebDriverException ignored) {
                // popup tidak ada / sudah tertutup
            }
        }
    }

    /** Mengumpulkan link produk unik (berdasarkan href) pada halaman daftar produk. */
    private Map<String, WebElement> collectProducts() {
        wait.until(d -> firstDisplayed(d, PRODUCT_LINKS) != null);
        Map<String, WebElement> products = new LinkedHashMap<>();
        for (WebElement link : driver.findElements(PRODUCT_LINKS)) {
            try {
                String href = link.getDomProperty("href"); // URL absolut hasil resolve browser
                if (href != null && link.isDisplayed()) {
                    products.putIfAbsent(href, link);
                }
            } catch (StaleElementReferenceException ignored) {
                // lewati
            }
        }
        return products;
    }

    private void screenshot(String name) {
        try {
            Files.createDirectories(screenshotDir);
            File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path target = screenshotDir.resolve(runId + "_" + name + ".png");
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            log("Screenshot: " + target);
        } catch (IOException | WebDriverException e) {
            log("Gagal menyimpan screenshot: " + e.getMessage());
        }
    }

    private static void log(String message) {
        System.out.printf("[%s] %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), message);
    }
}