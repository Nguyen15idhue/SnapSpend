using SnapSpend.Api.Models;

namespace SnapSpend.Api.Data;

/// <summary>
/// Dữ liệu seed cho engine nhận diện (thay toàn bộ list hardcode cũ).
/// Thêm case mới = thêm row ở đây + migration mới (không release app).
/// Keyword/pattern đã chuẩn hóa (thường + bỏ dấu), khớp logic RecognitionService.Normalize.
/// </summary>
public static class RecognitionSeed
{
    public static List<TotalKeyword> TotalKeywords()
    {
        var rows = new (string Kw, int Prio, bool Ex)[]
        {
            ("thanh toan", 100, false), ("tong cong", 90, false), ("total", 90, false),
            ("tong", 80, false), ("phai tra", 85, false), ("cong", 70, false),
            ("vat", 0, true), ("phu thu", 0, true), ("service", 0, true),
            ("truoc thue", 0, true), ("thue suat", 0, true), ("chiet khau", 0, true), ("giam gia", 0, true),
        };
        return rows.Select((r, i) => new TotalKeyword { Id = i + 1, Keyword = r.Kw, Priority = r.Prio, Exclude = r.Ex, IsActive = true }).ToList();
    }

    public static List<NoisePattern> NoisePatterns()
    {
        string[] rows =
        [
            "so (", "(no)", "so:", "ma so", "ma cqt", "ma hang", "ma khach hang", "khach hang",
            "ky hieu", "seri", "ma tra", "ngay", "date", "nam ", "dien thoai", "tel", "dia chi", "address",
            "ma nhan", "ma hd", "ma co quan", "so phieu", "so hd", "ban ", "ban:", "sl khach", "thoi gian", "gio ", "gio vao", "gio ra",
            "thu ngan", "phuc vu", "in luc", "ngay in", "dt:", "sdt", "hotline", "fax",
            "tien mat", "tien hang", "khach dua", "khach tra", "khach da tra", "con phai thu", "phai thu",
            "tien thua", "thoi lai", "tien thoi", "phi phuc vu", "phu thu", "thanh tien", "vat", "service", "tra cuu",
            "thuc uong", "thuc an", "subtotal", "sub total", "hoa don",
        ];
        return rows.Select((p, i) => new NoisePattern { Id = i + 1, Pattern = p, IsActive = true }).ToList();
    }

    public static List<CategoryKeyword> CategoryKeywords()
    {
        // Id ổn định theo nhóm (cách 100): thêm keyword mới vào cuối nhóm, không dịch Id cũ.
        var groups = new (string Cat, int Base, (string Kw, double W, string Src)[] Rows)[]
        {
            ("food", 1, [
                ("grabfood", 0.82, "merchant"), ("grab food", 0.82, "merchant"),
                ("shopeefood", 0.82, "merchant"), ("shopee food", 0.82, "merchant"),
                ("befood", 0.82, "merchant"), ("gofood", 0.82, "merchant"),
                ("pho", 0.82, "item"), ("com ", 0.82, "item"), ("com,", 0.82, "item"),
                ("bun", 0.82, "item"), ("mi ", 0.82, "item"), ("mi,", 0.82, "item"),
                ("hao hao", 0.82, "item"), ("banh mi", 0.82, "item"), ("banh", 0.82, "item"),
                ("an uong", 0.82, "item"), ("an sang", 0.82, "item"), ("an trua", 0.82, "item"),
                ("an toi", 0.82, "item"), ("do an", 0.82, "item"), ("mon an", 0.82, "item"),
                ("thuc an", 0.82, "item"), ("thuc pham", 0.82, "item"), ("cafe", 0.82, "item"),
                ("ca phe", 0.82, "item"), ("tra sua", 0.82, "item"), ("nha hang", 0.82, "item"),
                ("quan an", 0.82, "item"), ("lau ", 0.82, "item"), ("nuong", 0.82, "item"),
                ("canh ", 0.82, "item"), ("sup ", 0.82, "item"), ("chien ", 0.82, "item"),
                ("buffet", 0.82, "item"), ("food", 0.82, "item"), ("restaurant", 0.82, "item"),
                ("highlands", 0.82, "merchant"), ("phuc long", 0.82, "merchant"),
                ("the coffee house", 0.82, "merchant"), ("starbucks", 0.82, "merchant"),
                ("kfc", 0.82, "merchant"), ("lotteria", 0.82, "merchant"), ("jollibee", 0.82, "merchant"),
                ("pizza", 0.82, "item"), ("sushi", 0.82, "item"),
                ("di cho", 0.82, "item"), ("tap hoa", 0.82, "item"),
                ("bach hoa xanh", 0.82, "merchant"), ("winmart", 0.82, "merchant"),
                ("circle k", 0.82, "merchant"), ("ministop", 0.82, "merchant"), ("gs25", 0.82, "merchant"),
                ("family mart", 0.82, "merchant"), ("big c", 0.82, "merchant"), ("aeon", 0.82, "merchant"),
                ("coopmart", 0.82, "merchant"), ("mega market", 0.82, "merchant"),
                ("com phan", 0.82, "item"), ("com binh dan", 0.82, "item"), ("hu tieu", 0.82, "item"),
                ("che ", 0.82, "item"), ("sinh to", 0.82, "item"), ("nuoc mia", 0.82, "item"),
                ("nuoc ep", 0.82, "item"), ("an vat", 0.82, "item"), ("ga ran", 0.82, "item"),
                ("tra chanh", 0.82, "item"), ("tra dao", 0.82, "item"), ("caphe", 0.82, "item"),
                ("espresso", 0.82, "item"), ("latte", 0.82, "item"), ("grocery", 0.82, "item"),
                ("bakery", 0.82, "item"), ("cho dong", 0.82, "item"), ("cho ", 0.82, "item"),
                ("quan nhau", 0.85, "merchant"), ("hai san", 0.85, "merchant"),
                ("bia", 0.8, "item"), ("beer", 0.8, "item"), ("saporo", 0.75, "item"),
                ("sapporo", 0.75, "item"), ("carlsberg", 0.75, "item"), ("tiger", 0.75, "item"),
                ("heineken", 0.75, "item"), ("saigon pos", 0.7, "merchant"),
                ("soda", 0.8, "item"), ("cola", 0.8, "item"), ("coca", 0.8, "item"),
                ("sprite", 0.8, "item"), ("tonic", 0.75, "item"), ("sting", 0.75, "item"),
                ("nuoc ngot", 0.8, "item"),
            ]),
            ("shopping", 201, [
                ("mua sam", 0.8, "item"), ("shop", 0.8, "merchant"),
                ("quan ao", 0.8, "item"), ("ao thun", 0.8, "item"), ("ao so mi", 0.8, "item"),
                ("ao khoac", 0.8, "item"), ("ao len", 0.8, "item"), ("giay", 0.8, "item"),
                ("giay dep", 0.8, "item"), ("dep quai", 0.8, "item"), ("dep le", 0.8, "item"),
                ("tui xach", 0.8, "item"), ("dien may", 0.8, "item"), ("dien may xanh", 0.8, "merchant"),
                ("the gioi di dong", 0.8, "merchant"), ("fpt shop", 0.8, "merchant"),
                ("cellphones", 0.8, "merchant"), ("hoang ha", 0.8, "merchant"),
                ("mediamart", 0.8, "merchant"), ("nguyen kim", 0.8, "merchant"), ("cho lon", 0.8, "merchant"),
                ("gia dung", 0.8, "item"), ("sieu thi", 0.8, "item"),
                ("shopee", 0.8, "merchant"), ("lazada", 0.8, "merchant"), ("tiki", 0.8, "merchant"),
                ("quat", 0.8, "item"), ("tivi", 0.8, "item"), ("tu lanh", 0.8, "item"),
                ("zara", 0.8, "merchant"), ("uniqlo", 0.8, "merchant"), ("adidas", 0.8, "merchant"),
                ("nike", 0.8, "merchant"), ("my pham", 0.8, "item"), ("guardian", 0.8, "merchant"),
                ("hieu sach", 0.8, "item"), ("fahasa", 0.8, "merchant"), ("van phong pham", 0.8, "item"),
                ("noi that", 0.8, "item"), ("do choi", 0.8, "item"), ("concung", 0.8, "merchant"),
                ("con cung", 0.8, "merchant"), ("xiaomi", 0.8, "merchant"), ("samsung", 0.8, "merchant"),
                ("iphone", 0.8, "merchant"), ("apple", 0.8, "merchant"), ("oppo", 0.8, "merchant"),
                ("laptop", 0.8, "item"), ("tai nghe", 0.8, "item"), ("son moi", 0.8, "item"),
                ("mua ", 0.8, "item"),
            ]),
            ("transport", 301, [
                ("grab", 0.8, "merchant"), ("taxi", 0.8, "item"), ("xang", 0.8, "item"),
                ("ve xe ", 0.8, "item"), ("gui xe", 0.8, "item"), ("xe may", 0.8, "item"),
                ("xe om", 0.8, "item"), ("tien xe", 0.8, "item"), ("di xe", 0.8, "item"),
                ("bus", 0.8, "item"), ("tau hoa", 0.8, "item"), ("tau cao toc", 0.8, "item"),
                ("may bay", 0.8, "item"), ("be ", 0.8, "merchant"), ("grabbike", 0.8, "merchant"),
                ("grabcar", 0.8, "merchant"), ("grab bike", 0.8, "merchant"), ("grab car", 0.8, "merchant"),
                ("xanh sm", 0.8, "merchant"), ("gojek", 0.8, "merchant"), ("vietjet", 0.8, "merchant"),
                ("vietnam airlines", 0.8, "merchant"), ("bamboo", 0.8, "merchant"), ("ve may bay", 0.8, "item"),
                ("san bay", 0.8, "item"), ("ben xe", 0.8, "item"), ("ve tau", 0.8, "item"),
                ("duong sat", 0.8, "item"), ("metro", 0.8, "item"), ("petrolimex", 0.8, "merchant"),
                ("do xang", 0.8, "item"), ("rua xe", 0.8, "item"), ("sua xe", 0.8, "item"),
                ("thay nhot", 0.8, "item"), ("dau nhot", 0.8, "item"), ("dang kiem", 0.8, "item"),
                ("phi duong bo", 0.8, "item"), ("cao toc", 0.8, "item"), ("traveloka", 0.8, "merchant"),
                ("lop xe", 0.8, "item"),
            ]),
            ("entertainment", 401, [
                ("phim", 0.75, "item"), ("cgv", 0.75, "merchant"), ("game", 0.75, "item"),
                ("karaoke", 0.75, "item"), ("nhac", 0.75, "item"), ("concert", 0.75, "item"),
                ("du lich", 0.75, "item"), ("vui choi", 0.75, "item"), ("gym", 0.75, "item"),
                ("netflix", 0.75, "merchant"), ("spotify", 0.75, "merchant"), ("steam", 0.75, "merchant"),
                ("lotte cinema", 0.75, "merchant"), ("galaxy cinema", 0.75, "merchant"),
                ("massage", 0.75, "item"), ("spa", 0.75, "item"), ("cinema", 0.75, "item"),
                ("vinwonders", 0.75, "merchant"), ("dam sen", 0.75, "merchant"),
                ("suoi tien", 0.75, "merchant"), ("bao tang", 0.75, "item"),
                ("rap chieu phim", 0.75, "item"), ("khach san", 0.75, "item"),
                ("resort", 0.75, "item"), ("ve so", 0.75, "item"), ("lam dep", 0.75, "item"),
                ("mua ve", 0.75, "item"), ("ve xem phim", 0.75, "item"), ("kham pha", 0.75, "item"),
                ("bida", 0.85, "merchant"),
            ]),
            ("health", 501, [
                ("thuoc", 0.8, "item"), ("kham", 0.8, "item"), ("benh", 0.8, "item"),
                ("y te", 0.8, "item"), ("nha khoa", 0.8, "item"), ("suc khoe", 0.8, "item"),
                ("pharmacity", 0.8, "merchant"), ("long chau", 0.8, "merchant"), ("an khang", 0.8, "merchant"),
                ("nha thuoc", 0.8, "item"), ("quay thuoc", 0.8, "item"), ("hieu thuoc", 0.8, "item"),
                ("medlatec", 0.8, "merchant"), ("vinmec", 0.8, "merchant"), ("hoan my", 0.8, "merchant"),
                ("tam anh", 0.8, "merchant"), ("cho ray", 0.8, "merchant"), ("bach mai", 0.8, "merchant"),
                ("viet duc", 0.8, "merchant"), ("kham benh", 0.8, "item"), ("sieu am", 0.8, "item"),
                ("xet nghiem", 0.8, "item"), ("x quang", 0.8, "item"), ("noi soi", 0.8, "item"),
                ("tiem chung", 0.8, "item"), ("vacxin", 0.8, "item"), ("vaccine", 0.8, "item"),
                ("rang ham mat", 0.8, "item"), ("bao hiem y te", 0.8, "item"), ("kinh mat", 0.8, "item"),
            ]),
            ("education", 601, [
                ("hoc", 0.8, "item"), ("sach", 0.8, "item"), ("khoa hoc", 0.8, "item"),
                ("hoc phi", 0.8, "item"), ("truong", 0.8, "item"), ("truong hoc", 0.8, "item"),
                ("lop ", 0.8, "item"), ("gia su", 0.8, "item"), ("giao duc", 0.8, "item"),
                ("ielts", 0.8, "item"), ("toeic", 0.8, "item"), ("toefl", 0.8, "item"),
                ("luyen thi", 0.8, "item"), ("trung tam", 0.8, "item"), ("coursera", 0.8, "merchant"),
                ("udemy", 0.8, "merchant"), ("hoc vien", 0.8, "item"), ("dai hoc", 0.8, "item"),
                ("cao dang", 0.8, "item"), ("tieu hoc", 0.8, "item"), ("mam non", 0.8, "item"),
                ("dong phuc", 0.8, "item"), ("tap vo", 0.8, "item"), ("but bi", 0.8, "item"),
                ("cap sach", 0.8, "item"), ("hoc lieu", 0.8, "item"),
            ]),
            ("housing", 701, [
                ("cho thue", 0.78, "item"), ("thue nha", 0.78, "item"), ("tien thue", 0.78, "item"),
                ("tien phong", 0.78, "item"), ("phong tro", 0.78, "item"), ("chung cu", 0.78, "item"),
                ("ky tuc xa", 0.78, "item"), ("tien nha", 0.78, "item"), ("phi quan ly", 0.78, "item"),
                ("phi dich vu", 0.78, "item"), ("phi gui xe thang", 0.78, "item"), ("sua nha", 0.78, "item"),
                ("chong tham", 0.78, "item"), ("son nha", 0.78, "item"), ("ve sinh may lanh", 0.78, "item"),
            ]),
            ("bills", 801, [
                ("dien luc", 0.78, "item"), ("tien dien", 0.78, "item"), ("tien nuoc", 0.78, "item"),
                ("nuoc sach", 0.78, "item"), ("cap nuoc", 0.78, "item"), ("internet", 0.78, "item"),
                ("dien thoai", 0.78, "item"), ("cuoc", 0.78, "item"), ("truyen hinh", 0.78, "item"),
                ("gas", 0.78, "item"), ("hoa don", 0.78, "item"), ("evn", 0.78, "merchant"),
                ("vnpt", 0.78, "merchant"), ("viettel", 0.78, "merchant"), ("mobifone", 0.78, "merchant"),
                ("vinaphone", 0.78, "merchant"), ("fpt", 0.78, "merchant"), ("cmc", 0.78, "merchant"),
                ("sctv", 0.78, "merchant"), ("k+", 0.78, "item"), ("nap tien", 0.78, "item"),
                ("nap card", 0.78, "item"), ("momo", 0.78, "merchant"), ("zalopay", 0.78, "merchant"),
                ("vnpay", 0.78, "merchant"), ("chuyen khoan", 0.78, "item"), ("phi duy tri", 0.78, "item"),
            ]),
        };
        return groups.SelectMany(g => g.Rows.Select((r, i) => new CategoryKeyword
        {
            Id = g.Base + i, Category = g.Cat, Keyword = r.Kw, Weight = r.W, Source = r.Src, IsActive = true
        })).ToList();
    }

    public static List<CategoryAlias> CategoryAliases()
    {
        var rows = new (string Alias, string Cat)[]
        {
            ("an uong", "food"), ("do an", "food"), ("thuc pham", "food"), ("restaurant", "food"), ("meal", "food"),
            ("drink", "food"), ("cafe", "food"), ("coffee", "food"), ("food delivery", "food"), ("bakery", "food"),
            ("grocery", "food"), ("convenience", "food"), ("sieu thi mini", "food"),
            ("mua sam", "shopping"), ("do gia dung", "shopping"), ("electronics", "shopping"), ("appliance", "shopping"),
            ("household", "shopping"), ("goods", "shopping"), ("supermarket", "shopping"), ("sieu thi", "shopping"), ("market", "shopping"),
            ("di lai", "transport"), ("giao thong", "transport"), ("taxi", "transport"), ("grab", "transport"),
            ("fuel", "transport"), ("gas", "transport"), ("airline", "transport"),
            ("giai tri", "entertainment"), ("entertainment", "entertainment"), ("movie", "entertainment"),
            ("cinema", "entertainment"), ("hotel", "entertainment"),
            ("nha o", "housing"), ("housing", "housing"), ("rent", "housing"),
            ("suc khoe", "health"), ("health", "health"), ("medical", "health"), ("medicine", "health"),
            ("pharmacy", "health"), ("hospital", "health"),
            ("giao duc", "education"), ("hoc tap", "education"), ("education", "education"), ("school", "education"),
            ("hoa don", "bills"), ("bills", "bills"), ("utility", "bills"), ("utilities", "bills"),
            ("electricity", "bills"), ("water", "bills"), ("internet", "bills"), ("dien", "bills"),
            ("dien luc", "bills"), ("tien dien", "bills"),
            ("khac", "other"), ("other", "other"),
        };
        return rows.Select((r, i) => new CategoryAlias { Id = i + 1, Alias = r.Alias, Category = r.Cat, IsActive = true }).ToList();
    }

    public static List<BoilerplatePattern> BoilerplatePatterns()
    {
        string[] rows =
        [
            "ten don vi", "ma so thu", "mst", "dia chi", "address", "so dien thoai", "dien thoai", "tel",
            "can cuoc", "cccd", "hinh thuc thanh toan", "nguoi mua", "buyer", "nguoi ban", "seller",
            "kinh gui", "ma tra cuu", "ma nhan", "ky hieu", "cong tien", "thue suat", "tong tien", "viet bang chu",
            "tra cuu", "signature", "ngay", "date", "hoa don", "invoice", "stt", "don gia", "so luong", "sdt", "dt:",
            "ma cqt", "ten hang hoa", "ten hang", "ten mon", "mat hang", "mon sl", "description", "unit", "quantity", "code", "(no)", "(tar", "(pay", "(buyer",
        ];
        return rows.Select((p, i) => new BoilerplatePattern { Id = i + 1, Pattern = p, IsActive = true }).ToList();
    }
}
