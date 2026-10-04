# قرارداد API بک‌اند دوره‌های اسکرچ فارسی

آخرین تطبیق با کد سرور و اندروید: 2026-10-05. این سند قرارداد **پیاده‌شده فعلی** است؛ نمونه‌ها نمایشی‌اند و توکن‌ها/امضاهای نمونه قابل استفاده نیستند.

## آدرس و امنیت

- Base URL: `https://api.behnamapp.ir/scratch/v1`؛ در اپ `BuildConfig.COURSE_API_BASE`.
- روش انتقال HTTPS است. اپ ریدایرکت را دنبال نمی‌کند و URL تصویر/پیش‌نمایش باید هم‌مبدأ API باشد؛ userinfo، پورت غیرمعمول یا HTTP پذیرفته نمی‌شود.
- کاتالوگ عمومی است. دانلود پولی به **credential سرور** در `Authorization: Bearer …` نیاز دارد، نه توکن خرید استور.
- کلید خصوصی صحت‌سنجی بازار/مایکت فقط در سرور است. RSA عمومی هر استور در flavor همان استور قرار دارد؛ هیچ کلید خصوصی داخل اپ/این سند قرار نگیرد.
- ورود ادمین، کوکی پنل و Authenticator برای API عمومی اپ استفاده نمی‌شوند. Google Play Billing/Integrity بخشی از این قرارداد نیست.
- محدودیت عمومی فعلی 120 درخواست در دقیقه و مسیر تأیید/بازیابی 10 درخواست در دقیقه بر اساس IP است؛ این دو محدودیت همزمان اعمال می‌شوند. پاسخ 429 را با تأخیر مدیریت کنید؛ حلقه تکرار سریع ممنوع.
- پاسخ‌های کاتالوگ/خرید `private, no-store` هستند. API عمومی مستقل از session پنل است.

## قالب پاسخ

موفقیت کاتالوگ:

```json
{"data": {}, "meta": null, "error": null}
```

خطا معمولاً:

```json
{"data":null,"error":{"code":"validation_failed","message":"اطلاعات ارسالی معتبر نیست.","fields":{"sku":["…"]}}}
```

پاسخ‌های خرید `meta` ندارند و خطای آن‌ها ممکن است `fields` نداشته باشد. به کد HTTP و `error.code` توجه کنید؛ متن فارسی قرارداد ماشینی نیست. همه تاریخ‌های سرور UTC هستند؛ زمان خرید استور بر حسب میلی‌ثانیه Unix است. UUID رشته‌ای است؛ با شناسه عددی داخلی دیتابیس اشتباه نشود.

## 1. فهرست دوره‌ها

`GET /courses`

پارامترهای اختیاری: `page` بین 1 و 1000، `q` حداکثر 120 کاراکتر برای عنوان، `difficulty` بین 1 و 5، `featured` بولی (برای HTTP مقدار 0/1). صفحه‌بندی 20 دوره در هر صفحه و ترتیب `sort_order` سپس `id` است؛ تا `meta.has_more=false` ادامه دهید. فقط دوره‌های منتشرشده، فعال و بایگانی‌نشده برمی‌گردند.

```json
{
  "data": [{
    "uuid": "ae6b3b4c-1d11-43fe-bd17-1c80ad234666",
    "title": "آموزش اسکرچ",
    "slug": "scratch-basic",
    "short_description": "ساخت اولین پروژه",
    "description": "شرح کامل دوره",
    "instructor_name": "نام مدرس",
    "difficulty": 1,
    "is_featured": true,
    "cover": {"type":"image","url":"https://api.behnamapp.ir/scratch/v1/media/7d6b3b4c-1d11-43fe-bd17-1c80ad234666","poster_url":null,"version":1,"duration_seconds":null},
    "banner_url": null,
    "stats": {"lessons":4,"duration_seconds":1200,"confirmed_purchases":12},
    "products": {"cafebazaar":"scratch_basic","myket":"scratch_basic"},
    "access": {"has_access":false}
  }],
  "meta": {"page":1,"has_more":false},
  "error": null
}
```

`products` نگاشت استور به SKU فعال است، نه آرایه قیمت. در حالت خالی `{}` است. اپ فقط دوره‌های دارای SKU معتبر برای flavor مربوط را نمایش می‌دهد. شناسه `scratch_basic` مربوط به دوره فعلی است؛ دوره‌های دیگر می‌توانند SKU دیگری داشته باشند. SKU همان دوره باید در پنل استور نیز موجود باشد.

قیمت نمایش/پرداخت از SDK همان استور برای همان SKU خوانده می‌شود. قیمت ثبت‌شده در پنل سرور برای گزارش مبلغ خرید است و در قرارداد فعلی کاتالوگ ارسال نمی‌شود؛ قیمت واقعی استور را تغییر نمی‌دهد. قیمت قدیمی کاتالوگ سند مبلغ پرداخت تراکنش نیست.

`confirmed_purchases` تعداد رسیدهای تأییدشده است، نه دانشجوی یکتا؛ ممکن است `null` باشد. `difficulty` کلی دوره موجود است ولی طبق درخواست مالک، UI کارت دوره آن را نمایش نمی‌دهد.

## 2. جزئیات دوره

`GET /courses/{course_uuid}`

`data` یک شیء با همان فیلدهای دوره در فهرست است؛ `meta` برابر `null`. دوره ناموجود/غیرمنتشرشده 404 می‌دهد. با bearer معتبر، `access.has_access` می‌تواند true باشد؛ پاسخ عمومی false اثبات نبود خرید کاربر نیست.

## 3. فهرست فصل‌ها و درس‌ها

`GET /courses/{course_uuid}/content`

```json
{
  "data": {
    "course_uuid":"ae6b3b4c-1d11-43fe-bd17-1c80ad234666",
    "sections":[{
      "id":1,"title":"شروع","description":"",
      "lessons":[{
        "uuid":"8d6b3b4c-1d11-43fe-bd17-1c80ad234666",
        "course_uuid":"ae6b3b4c-1d11-43fe-bd17-1c80ad234666",
        "title":"درس اول","subtitle":"","description":"شرح درس",
        "difficulty":1,"is_preview":true,"cover":null,
        "video":{
          "url":"https://api.behnamapp.ir/scratch/v1/lessons/8d6b3b4c-1d11-43fe-bd17-1c80ad234666/video/7d6b3b4c-1d11-43fe-bd17-1c80ad234666?expires=1900000000&signature=EXAMPLE",
          "duration_seconds":300,"file_size_bytes":1048576,"mime_type":"video/mp4",
          "content_version":1,"content_hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        },
        "access":{"has_access":true}
      }]
    }]
  },"meta":null,"error":null
}
```

هش نمونه معتبر برای هیچ فایل واقعی نیست؛ از SHA-256 واقعی بایت‌های دانلودشده استفاده کنید. بخش `video` یا `cover` می‌تواند `null` باشد. اپ فعلی فقط درس دارای metadata ویدئوی معتبر را وارد دانلود می‌کند. فصل غیرفعال و درس پیش‌نویس/بایگانی‌شده وارد فهرست نمی‌شود. ترتیب خروجی سرور حفظ می‌شود.

`GET /lessons/{lesson_uuid}` نیز یک درس با همین ساختار می‌دهد؛ برای تازه‌کردن metadata یک درس قابل استفاده است.

سختی درس: 1 مقدماتی 🌱، 2 آسان 🙂، 3 متوسط 💡، 4 پیشرفته 🚀، 5 حرفه‌ای 🔥. درجه نامشخص بدج ندارد. در اپ بدج فقط روی درس نمایش داده می‌شود.

## 4. پیش‌نمایش رایگان

اگر `is_preview=true` باشد، درس منتشرشده **بدون خرید** قابل دانلود و تماشا است. رایگان بودن یک درس به معنی رایگان بودن کل دوره نیست.

- API در `video.url` یک URL کامل امضاشده با اعتبار حدود یک ساعت می‌دهد.
- مسیر: `GET /lessons/{lesson_uuid}/video/{asset_uuid}?expires=…&signature=…`.
- URL را با query اصلی، بدون تغییر/بازسازی امضا استفاده کنید؛ app کلید امضای سرور ندارد.
- هیچ bearer، receipt یا کلید استور به این درخواست اضافه نکنید.
- اپ قبل از دانلود، کاتالوگ درس را دوباره دریافت می‌کند تا لینک تازه و رایگان بودن فعلی بررسی شوند. URL امضاشده در cache پایدار ذخیره نمی‌شود.
- حذف امضا یا انقضای آن 403 می‌دهد؛ metadata را تازه کنید، نه بازیابی خرید.
- غیرفعال شدن پیش‌نمایش/حذف انتشار می‌تواند 404 بدهد، حتی با امضای قبلی معتبر.
- پاسخ کامل 200 و پاسخ Range معتبر 206 است. HEAD نیز پشتیبانی می‌شود.
- اپ بعد از تطبیق اندازه و SHA-256، فایل را خصوصی ذخیره می‌کند و پیش‌نمایش دانلودشده را بدون خرید آفلاین پخش می‌کند. تغییر مجوز سرور هنگام اتصال بعدی از metadata تازه اعمال می‌شود؛ پاکسازی/لغو فوری فایل آفلاین تضمین نیست.

## 5. تأیید و بازیابی خرید

`POST /purchases/verify` یا `POST /purchases/restore`

هدر `Content-Type: application/json` و بدنه:

```json
{"provider":"cafebazaar","sku":"scratch_basic","purchase_token":"STORE_RECEIPT_TOKEN"}
```

`provider` فقط `cafebazaar` یا `myket` است. SKU 1 تا 160 کاراکتر از حروف/عدد/نقطه/خط تیره/زیرخط؛ purchase_token حداکثر 4096 کاراکتر و بدون فاصله/کاراکتر کنترلی. پکیج از تنظیمات امن سرور خوانده می‌شود، نه بدنه کلاینت.

```json
{
  "data": {
    "purchase_id":"9d6b3b4c-1d11-43fe-bd17-1c80ad234666",
    "course_uuid":"ae6b3b4c-1d11-43fe-bd17-1c80ad234666",
    "status":"verified","has_access":true,
    "access_token":"64_LOWERCASE_HEX_CHARACTERS_FROM_SERVER",
    "token_type":"Bearer","expires_at":"2026-10-05T12:00:00+00:00"
  },"error":null
}
```

توکن نمونه بالا واقعی/مطابق قالب نیست؛ credential واقعی دقیقاً 64 رقم هگز کوچک است. اعتبار فعلی حدود یک ساعت است. تطبیق `course_uuid` با دوره انتخاب‌شده قبل از ذخیره دسترسی الزامی است.

دو مسیر از یک منطق idempotent صحت‌سنجی استفاده می‌کنند؛ ارسال دوباره همان رسید رکورد مالی تازه نمی‌سازد. سرور هیچ پرداخت جدیدی انجام نمی‌دهد. اپ پیش از payment، owned را در SDK بررسی می‌کند؛ خطای استور هرگز به معنی «نخریده» نیست. بازیابی بدون مالکیت پرداخت تازه شروع نمی‌کند. محصول غیرمصرفی است؛ **consume نکنید**.

رسید قبل از HTTP در Keystore-encrypted vault ذخیره می‌شود. پس از قطع شبکه فقط با owned/restore ادامه دهید. دریافت SDK موفق به تنهایی دسترسی پولی نمی‌دهد؛ تأیید سرور ضروری است. account/store-user-id این معماری پیاده نشده است؛ مالکیت بر اساس رسید و اتصال SKU/course است.

هر بازیابی credential قبلی آن رسید را تعویض می‌کند؛ دستگاه دیگر ممکن است به بازیابی نیاز پیدا کند. مسدود بودن مدیریتی با `access_disabled` برمی‌گردد و با restore دور زده نمی‌شود.

## 6. دسترسی فعلی و دانلود پولی

`GET /purchases/access` با `Authorization: Bearer <access_token>`:

```json
{"data":{"purchase_id":"UUID","course_uuid":"UUID","has_access":true},"error":null}
```

credential نامعتبر/منقضی/مسدود 401 می‌دهد. این مسیر هویت استور یا قیمت خرید را ارائه نمی‌کند.

`GET /lessons/{lesson_uuid}/purchased-video` با همان bearer:

- فقط درس منتشرشده متعلق به دوره آن خرید مجاز است؛ خرید یک دوره دیگری را باز نمی‌کند.
- دانلود کامل 200، Range معتبر 206، ویدئو `video/mp4`، `Cache-Control: private, no-store`.
- endpoint فایل metadata JSON برنمی‌گرداند. هنگام نداشتن دسترسی 403 و نبود محتوا 404 است.
- اپ metadata درس پولی را بدون token نیز دریافت می‌کند؛ `video.url` ممکن است null باشد. پس از تأیید، مسیر ثابت purchased-video را همراه credential همان دوره استفاده می‌کند.
- 401/403 دانلود پولی یک بار restore و retry می‌شود. خطای شبکه رسید را پاک نمی‌کند.
- پخش فایل دانلودشده نیازمند سابقه تأیید همان دوره است؛ لغو شناخته‌شده سرور flag محلی را غیرفعال می‌کند، ولی فایل و رسید را پاک نمی‌کند.

## 7. تصاویر و کاور

`GET /media/{asset_uuid}` برای کاور/پوستر منتشرشده و عمومی؛ از `cover.poster_url` یا `cover.url` با `type=image` استفاده کنید. کاور ویدئویی می‌تواند type=video باشد؛ اپ برای thumbnail از پوستر استفاده می‌کند. کلید داخلی storage در JSON نیست.

خود ویدئوی پولی با حدس زدن `/media` عمومی نمی‌شود. ریدایرکت/مبدأ دیگر برای کاور در اپ رد می‌شود و placeholder محلی می‌آید. اپ حداکثر 5 MiB تصویر می‌خواند و decode را sample می‌کند.

## 8. فایل، کش و خطا

- نام فایل شامل lesson UUID، content_version و hash است. تغییر نسخه/هش فایل دیگری ایجاد می‌کند؛ cache یک دوره به دیگری منتقل نمی‌شود.
- دانلود ادامه‌پذیر `.part` خصوصی است؛ Range نادیده گرفته‌شده با پاسخ 200 باعث شروع از صفر می‌شود، نه چسباندن دو فایل.
- Content-Length، Content-Range، MIME، اندازه نهایی و SHA-256 بررسی می‌شوند؛ فایل ناقص وارد «تماشا» نمی‌شود.
- download فعلی sequential و foreground است؛ خروج/بسته شدن اپ ممکن است ادامه را متوقف کند. پاک شدن داده برنامه، فایل‌ها/کلیدها را از بین می‌برد؛ بازیابی از استور انجام می‌شود.
- کش catalog چنددوره‌ای و کش lesson جدا برای هر course است؛ کش قدیمی تک‌دوره‌ای fallback دارد. metadata قدیمی بدون is_preview به صورت پولی تفسیر می‌شود.
- داده کاتالوگ در اپ حداکثر 2 MiB در هر پاسخ و حداکثر 50 صفحه خوانده می‌شود؛ رسید و credential وارد log یا cache کاتالوگ نمی‌شوند.

خطاهای مهم: 404 `not_found`، 422 `validation_failed` یا `refunded`/`rejected`، 403 `access_disabled`، 409 تعارض تنظیمات/مالکیت، 503 `service_unavailable`/`configuration_error`/`pending` و 429 محدودیت درخواست. بعضی ردهای عمومی `request_rejected` هستند؛ کد خطا به تنهایی بدون HTTP کافی نیست. در اپ `not_owned` خطای محلی SDK است، نه الزاماً پاسخ API.

روی `refunded`, `rejected`, `not_owned`, `access_disabled` دسترسی پولی محلی لغو می‌شود. timeout/503/قطع شبکه نباید خرید را جعلی فرض کند. دانلود پیش‌نمایش شکست‌خورده نباید به خرید/استور وابسته شود.

## 9. تنظیم و آزمون تحویل

1. دوره و درس منتشر، فعال و دارای ویدئوی پردازش‌شده باشند. برای هر استور محصول فعال به دوره متصل شود.
2. یک درس is_preview=true و یکی false: بدون خرید فقط رایگان دانلود/پخش شود و endpoint پولی رد شود.
3. URL منقضی، بدون امضا، دامنه دیگر و UUID درس دیگر رد شوند؛ URL تازه از کاتالوگ گرفته شود.
4. خرید، restore، انقطاع بعد پرداخت، تغییر حساب استور، مسدودشدن مدیریتی و دریافت مجدد credential روی دستگاه واقعی آزمایش شوند.
5. دو دوره با SKU متفاوت: قیمت، خرید، cache، دسترسی و دانلودها مستقل باشند.
6. آموزش ابتدا گرید دو ستون دارد؛ تک‌دوره کارت افقی است. پس از انتخاب، درس‌ها دو ستون و فصل‌ها عنوان سراسری دارند. سختی کلی دوره مخفی است؛ سختی هر درس بدج رنگی/ایموجی دارد.
7. بیلد/تست محلی جای آزمون واقعی پرداخت با امضا و تنظیمات استور و CDN را نمی‌گیرد.

فایل‌های مرجع اپ: `CourseApi.kt`, `CourseModels.kt`, `PurchaseVault.kt`, `CourseCache.kt`, `LessonDownloads.kt`, `TrainingController.kt`, `TrainingScreen.kt`. مرجع سرور: `routes/api.php`, `CatalogService`, `ContentController`, `PurchaseAccess`, `PurchaseController`, `StorePurchaseVerifier` در پروژه `behnamapp-lyrics-platform`.
