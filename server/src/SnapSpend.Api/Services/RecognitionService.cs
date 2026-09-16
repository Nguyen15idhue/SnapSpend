using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
using SnapSpend.Api.Models;

namespace SnapSpend.Api.Services;

public record ReceiptItemDto(string Name, long Amount);
public record ReceiptParse(long? Total, string Level, List<ReceiptItemDto> Items, string Category, double Confidence, string Summary);

/// <summary>
/// Engine nhận diện duy nhất: đọc keyword từ DB (không hardcode).
/// Tổng: thang priority + dòng quyết toán cuối + loại trừ VAT/phụ thu; hòa thì dòng sau thắng.
/// Món: số CUỐI dòng = thành tiền, giữ dòng trùng tên. Danh mục: merchant +10 rồi so dài nhất, hòa merchant thắng.
/// </summary>
public class RecognitionService(AppDbContext db)
{
    private const long MinAmount = 1000;
    private const long MaxAmount = 9_999_999_999;
    private const double TolerancePct = 5.0;
    // Token số VN: "1.053.000", "7751.000", "225,000", "1000" -> bỏ hết phân cách rồi parse.
    private static readonly Regex NumberRegex = new(@"[\d.,]+", RegexOptions.Compiled);
    // Thứ tự danh mục khi hòa (giữ parity với Rules cũ).
    private static readonly string[] CategoryOrder = ["food", "shopping", "transport", "entertainment", "housing", "health", "education", "bills", "other"];

    private List<TotalKeyword>? _totals;
    private List<NoisePattern>? _noise;
    private List<CategoryKeyword>? _catKw;
    private List<CategoryAlias>? _aliases;
    private List<BoilerplatePattern>? _boiler;

    private async Task LoadAsync()
    {
        if (_totals is not null) return;
        _totals = await db.TotalKeywords.Where(x => x.IsActive).ToListAsync();
        _noise = await db.NoisePatterns.Where(x => x.IsActive).ToListAsync();
        _catKw = await db.CategoryKeywords.Where(x => x.IsActive).ToListAsync();
        _aliases = await db.CategoryAliases.Where(x => x.IsActive).ToListAsync();
        _boiler = await db.BoilerplatePatterns.Where(x => x.IsActive).ToListAsync();
    }

    /// <summary>
    /// Bỏ dấu tiếng Việt + lower để so khớp không phụ thuộc dấu ("Phở" = "Pho").
    /// LƯU Ý: chữ đ/Đ KHÔNG phân rã trong Unicode FormD nên phải map tay, nếu không
    /// mọi keyword chứa d gốc-đ ("tien dien", "di cho", "dia chi", "khach da tra"...) đều khớp hụt.
    /// </summary>
    public static string Normalize(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return "";
        var formD = text.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(formD.Length);
        foreach (var ch in formD)
            if (CharUnicodeInfo.GetUnicodeCategory(ch) != UnicodeCategory.NonSpacingMark)
                sb.Append(ch);
        return sb.ToString().Normalize(NormalizationForm.FormC).ToLowerInvariant().Replace("đ", "d");
    }

    private static List<long> Numbers(string line) =>
        NumberRegex.Matches(line).Select(m => m.Value.Replace(".", "").Replace(",", ""))
            .Select(v => long.TryParse(v, out var n) ? n : 0).Where(n => n is >= MinAmount and <= MaxAmount).ToList();

    private bool IsNoise(string normLine) => _noise!.Any(p => normLine.Contains(Normalize(p.Pattern)));

    /// <summary>
    /// Keyword danh mục khớp theo ranh giới từ (tránh "nike" trong "heniken", "bus" trong "business").
    /// Keyword vốn có dấu cách biên ("com ", "be ") giữ nguyên ý nghĩa biên đó.
    /// </summary>
    private static bool KeywordHit(string text, string kw)
    {
        if (kw.Length == 0) return false;
        var idx = 0;
        while ((idx = text.IndexOf(kw, idx, StringComparison.Ordinal)) >= 0)
        {
            var leftOk = kw[0] == ' ' || idx == 0 || !IsWordChar(text[idx - 1]);
            var end = idx + kw.Length;
            var rightOk = kw[^1] == ' ' || end >= text.Length || !IsWordChar(text[end]);
            if (leftOk && rightOk) return true;
            idx += 1;
        }
        return false;
    }

    private static bool IsWordChar(char c) => (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');

    /// <summary>Số ở dòng Tổng cuối cùng (ưu tiên priority cao, hòa thì dòng sau); null khi không có.</summary>
    public async Task<long?> ExtractTotalAsync(string text)
    {
        await LoadAsync();
        if (string.IsNullOrWhiteSpace(text)) return null;
        long? best = null;
        var bestPrio = -1;
        foreach (var raw in text.Split('\n'))
        {
            var line = Normalize(raw);
            if (line.Length == 0) continue;
            // Tầng total chỉ xét từ khóa tổng + loại trừ (không xét noise: "Thành tiền Total" vừa là noise-item vừa là dòng tổng).
            var matched = _totals!.Where(k => line.Contains(Normalize(k.Keyword))).ToList();
            if (matched.Count == 0) continue;
            if (matched.Any(k => k.Exclude)) continue;
            var prio = matched.Max(k => k.Priority);
            var hit = Numbers(raw);
            if (hit.Count == 0) continue;
            // Priority cao thắng; hòa thì dòng sau (quyết toán in sau) thắng.
            if (prio >= bestPrio) { bestPrio = prio; best = hit.Max(); }
        }
        return best;
    }

    public async Task<List<ReceiptItemDto>> ParseItemsAsync(string text)
    {
        await LoadAsync();
        var items = new List<ReceiptItemDto>();
        if (string.IsNullOrWhiteSpace(text)) return items;
        var totalWords = _totals!.Where(k => !k.Exclude).Select(k => Normalize(k.Keyword)).ToList();
        // Dòng tên (có chữ, không số tiền) + dòng số tiếp theo = 1 món (bill in tên/số khác dòng).
        string? pending = null;
        foreach (var raw in text.Split('\n').Select(l => l.Trim()))
        {
            if (raw.Length < 6) continue;
            var line = Normalize(raw);
            // Bỏ dòng tổng + dòng nhiễu (MST/mã/giờ/phone/thanh toán... — tất cả trong DB).
            if (totalWords.Any(w => line.Contains(w)) || IsNoise(line)) { pending = null; continue; }
            var list = Numbers(raw);
            if (list.Count == 0)
            {
                var nm = CleanName(raw);
                if (nm.Count(char.IsLetter) >= 3 && !IsBoilerplate(line)) pending = nm;
                continue;
            }
            string name;
            if (!raw.Any(char.IsLetter))
            {
                // Dòng toàn số (mã/phone/tổng lẻ): chỉ ghép khi có tên chờ + đủ 2 số (SL/đơn giá/thành tiền).
                if (pending is null || list.Count < 2) { pending = null; continue; }
                name = pending;
            }
            else name = pending is null ? CleanName(raw) : (pending + " " + CleanName(raw)).Trim();
            pending = null;
            if (name.Count(char.IsLetter) < 3) continue;
            items.Add(new(name.Length > 80 ? name[..80] : name, list.Last()));
        }
        return items;
    }

    private static string CleanName(string raw) =>
        Regex.Replace(Regex.Replace(NumberRegex.Replace(raw, " "), @"[^\p{L}\p{N}\s]", " "), @"\s+", " ").Trim();

    private bool IsBoilerplate(string normLine) => _boiler!.Any(p => normLine.Contains(Normalize(p.Pattern)));

    public static bool ValidateTotal(long total, List<ReceiptItemDto> items)
    {
        if (total <= 0) return false;
        var sum = items.Sum(i => i.Amount);
        if (sum <= 0) return false;
        return Math.Abs(sum - total) * 100.0 / total <= TolerancePct;
    }

    /// <summary>Đoán tổng kèm mức tin cậy: HIGH = tự điền; MEDIUM = điền + nhắc; LOW = trống + hint.</summary>
    public async Task<(long? Amount, string Level)> GuessAmountAsync(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return (null, "LOW");
        var total = await ExtractTotalAsync(text);
        if (total is not null)
        {
            var items = await ParseItemsAsync(text);
            return items.Count == 0 || ValidateTotal(total.Value, items) ? (total, "HIGH") : (total, "MEDIUM");
        }
        var sum = (await ParseItemsAsync(text)).Sum(i => i.Amount);
        if (sum > 0) return (sum, "MEDIUM");
        var lines = text.Split('\n').Where(l => !IsNoise(Normalize(l))).SelectMany(Numbers).ToList();
        return lines.Count > 0 ? (lines.Max(), "LOW") : (null, "LOW");
    }

    /// <summary>
    /// Phân loại text: từ khóa merchant (tên cửa hàng) được +10 độ dài hiệu dụng rồi mới so dài nhất;
    /// hòa thì merchant thắng, rồi tới thứ tự seed. VD "bida"(+10) thắng "thuoc" ở bill bida.
    /// </summary>
    public async Task<ClassificationResult> ClassifyTextAsync(string? note)
    {
        await LoadAsync();
        // Chỉ phân loại trên dòng nội dung (bỏ nhiễu + boilerplate): dòng thanh toán/chân trang
        // (VD "Chuyển khoản", "Chi tiết hoá đơn") không được lái danh mục.
        var lines = (note ?? "").Split('\n').Select(l => l.Trim()).Where(l => l.Length > 0).ToList();
        var kept = lines.Where(l => { var n = Normalize(l); return !IsNoise(n) && !IsBoilerplate(n); }).ToList();
        var s = Normalize(string.Join("\n", kept.Count > 0 ? kept : lines));
        if (s.Length == 0) return new("other", 0.2, []);
        string? bestCat = null;
        var bestScore = -1;
        var bestMerchant = false;
        var bestWeight = 0.0;
        var matched = new List<string>();
        foreach (var k in _catKw!)
        {
            var kw = Normalize(k.Keyword);
            if (!KeywordHit(s, kw)) continue;
            if (!matched.Contains(k.Category)) matched.Add(k.Category);
            var merchant = k.Source == "merchant";
            var score = kw.Length + (merchant ? 10 : 0);
            if (score > bestScore || (score == bestScore && merchant && !bestMerchant))
            { bestScore = score; bestMerchant = merchant; bestCat = k.Category; bestWeight = k.Weight; }
        }
        if (bestCat is null) return new("other", 0.3, []);
        return new(bestCat, bestWeight, matched);
    }

    /// <summary>Chuẩn hóa nhãn model (key/tiếng Việt/đồng nghĩa) về key hợp lệ.</summary>
    public async Task<string?> MapCategoryAsync(string? raw)
    {
        await LoadAsync();
        if (string.IsNullOrWhiteSpace(raw)) return null;
        var norm = Normalize(raw);
        if (CategoryOrder.Contains(norm)) return norm;
        return _aliases!.FirstOrDefault(a => Normalize(a.Alias) == norm)?.Category;
    }

    /// <summary>Tóm tắt nội dung (giữ tên món), bỏ boilerplate; tối đa 120 ký tự.</summary>
    public async Task<string> SummarizeAsync(string text, int maxLen = 120)
    {
        await LoadAsync();
        if (string.IsNullOrWhiteSpace(text)) return "";
        var items = text.Split('\n').Select(l => l.Trim())
            .Where(l => l.Length >= 4)
            .Where(l => !IsBoilerplate(Normalize(l)))
            .Where(l => l.Count(char.IsLetter) >= 3).ToList();
        var joined = Regex.Replace(string.Join(", ", items), @"\s+", " ").Trim();
        var s = joined.Length == 0 ? text : joined;
        return s.Length > maxLen ? s[..maxLen] : s;
    }

    public async Task<ReceiptParse> ParseAsync(string text)
    {
        var (amount, level) = await GuessAmountAsync(text);
        var items = await ParseItemsAsync(text);
        // Phân loại trên toàn văn để bắt tên merchant (VD "Bida" thắng "bia" nhờ dài hơn + merchant).
        var cls = await ClassifyTextAsync(text);
        // Tóm tắt ưu tiên tên món đã tách (đủ ý nghĩa trong 120 ký tự); không món thì lọc dòng.
        var summary = items.Count > 0
            ? string.Join(", ", items.Select(i => i.Name))
            : await SummarizeAsync(text);
        if (summary.Length > 120) summary = summary[..120];
        return new(amount, level, items, cls.Category, cls.Confidence, summary);
    }
}
