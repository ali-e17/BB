# قرارداد اولیه API نسخه ۱

Base URL نهایی: `https://YOUR_DOMAIN/api/v1`

همه پاسخ‌ها JSON هستند. مسیرهای محافظت‌شده هدر `Authorization: Bearer <token>` می‌خواهند. رمز در PHP فقط با `password_hash()` ذخیره و با `password_verify()` بررسی می‌شود؛ اپ هیچ‌گاه به MySQL مستقیم وصل نمی‌شود.

## احراز هویت

- `POST /auth/login` — ورودی: `phone`, `password`؛ خروجی: token و اطلاعات نقش/کاربر.
- `POST /auth/change-password` — ورودی: `current_password`, `new_password`.
- `POST /auth/logout` — باطل کردن token فعلی.

## کاربران (فقط مدیر)

- `GET/POST /students`
- `GET/PATCH /students/{id}` و `PATCH /students/{id}/status`
- `GET/POST /teachers`
- `GET/PATCH /teachers/{id}` و `PATCH /teachers/{id}/status`

در ساخت کاربر، رمز اولیه همان کد ملی است اما سرور هش آن را ذخیره می‌کند.

## کلاس و عضویت

- `GET/POST /classes`
- `GET/PATCH /classes/{id}`
- `POST /classes/{id}/complete` — پایان ترم بدون حذف تاریخچه.
- `POST /classes/{id}/teacher` — تخصیص یک استاد.
- `POST /classes/{id}/students` — افزودن دانش‌آموز و بستن عضویت فعال قبلی در یک transaction.
- `DELETE /classes/{id}/students/{studentId}` — پایان عضویت، نه حذف رکورد.

## حضورغیاب

- `GET /classes/{id}/attendance?date=YYYY-MM-DD`
- `POST /classes/{id}/attendance/finalize` — ثبت یک‌مرحله‌ای جلسه و ردیف‌ها در transaction.

برای وضعیت `LATE`، `delay_minutes > 0` اجباری است. endpoint و دیتابیس هیچ مسیر ویرایش/حذف برای رکورد نهایی ارائه نمی‌کنند. همان transaction برای `LATE` و `ABSENT` اعلان اختصاصی دانش‌آموز می‌سازد.

## اعلان‌ها

- `GET /announcements` — سرور بر اساس نقش و عضویت فیلتر می‌کند.
- `POST /announcements` — مدیر: همه/استادان/کلاس؛ استاد: فقط کلاس تحت تدریس خودش.
- `POST /announcements/{id}/attachment` — رزرو برای مرحله پیوست.

## کارنامه

- `POST /classes/{id}/report-template`
- `POST /report-templates/{id}/publish` — فقط مدیر؛ همه نمره‌ها را transactionally منتشر می‌کند.
- `GET /students/me/report-cards` — تاریخچه همه کارنامه‌های منتشرشده حساب دانش‌آموز.

کدهای پایه: `200/201` موفق، `400` اعتبارسنجی، `401` ورود نامعتبر، `403` عدم دسترسی، `404` پیدا نشد، `409` تعارض یا ثبت تکراری، `422` داده نامعتبر و `500` خطای داخلی با پیام عمومی.

