using System.Text;
using System.Text.Json;

namespace SnapSpend.Api.Services;

public record ClassificationResult(string Category, double Confidence, List<string> Candidates);
public record AnalysisResult(string Summary, List<string> Trends, List<string> Anomalies, List<string> Recommendations);

/// <summary>
/// Gọi Google Gemini API (generateContent) để phân loại ảnh chi tiêu và phân tích hành vi.
/// Không có API key hoặc gọi lỗi thì tự fallback heuristic/analysis mẫu (không chặn luồng).
/// Cấu hình: Ai:ApiKey, Ai:Model, Ai:BaseUrl.
/// </summary>
public class AiService(IConfiguration config, IHttpClientFactory httpClientFactory)
{
    private static readonly string[] Allowed = ["food", "shopping", "transport", "entertainment", "housing", "health", "education", "bills", "other"];

    private string? ApiKey => config["Ai:ApiKey"];
    private string Model => config["Ai:Model"] is { Length: > 0 } m ? m : "gemini-3.8-flash";
    private string BaseUrl => (config["Ai:BaseUrl"] is { Length: > 0 } b ? b : "https://generativelanguage.googleapis.com/v1beta").TrimEnd('/');

    public async Task<ClassificationResult> ClassifyAsync(string? imagePath, string? note)
    {
        if (!string.IsNullOrWhiteSpace(note)) return Heuristic(note);
        if (string.IsNullOrWhiteSpace(imagePath) || !File.Exists(imagePath)) return Heuristic(note);
        var bytes = await File.ReadAllBytesAsync(imagePath);
        var mime = Path.GetExtension(imagePath).ToLowerInvariant() switch { ".png" => "image/png", ".webp" => "image/webp", _ => "image/jpeg" };
        return await ClassifyAsync(bytes, mime, note);
    }

    /// <summary>
    /// Phân loại: có ghi chú -> engine nội bộ (tức thời, không phụ thuộc mạng/quota);
    /// không có ghi chú -> thử Gemini theo ảnh (timeout ngắn), lỗi thì fallback.
    /// </summary>
    public async Task<ClassificationResult> ClassifyAsync(byte[]? image, string? mime, string? note)
    {
        if (!string.IsNullOrWhiteSpace(note)) return Heuristic(note);

        var key = ApiKey;
        if (string.IsNullOrWhiteSpace(key) || image is not { Length: > 0 }) return Heuristic(note);

        try
        {
            var parts = new List<object>
            {
                new { text = "Bạn phân loại một ảnh hóa đơn/chi tiêu tiếng Việt. Đọc nội dung và chọn ĐÚNG MỘT key sau:\n"
                    + "- food: ăn uống, nhà hàng, quán ăn, mì, bún, cơm, cà phê, trà sữa\n"
                    + "- shopping: mua sắm đồ vật, quần áo, điện máy, quạt, đồ gia dụng, siêu thị\n"
                    + "- transport: đi lại, xăng, taxi, grab, vé xe, gửi xe\n"
                    + "- entertainment: giải trí, phim, game, karaoke\n"
                    + "- housing: tiền nhà, thuê nhà, chung cư\n"
                    + "- health: sức khỏe, thuốc, khám bệnh, bệnh viện\n"
                    + "- education: giáo dục, học phí, sách vở, khóa học\n"
                    + "- bills: hóa đơn điện, nước, internet, điện thoại, phí định kỳ\n"
                    + "- other: không thuộc nhóm nào\n"
                    + "Chỉ trả JSON: {\"category\":\"food\",\"confidence\":0.0}" },
                new { inline_data = new { mime_type = string.IsNullOrWhiteSpace(mime) ? "image/jpeg" : mime, data = Convert.ToBase64String(image) } }
            };
            var text = await GenerateAsync(key, parts);
            using var doc = JsonDocument.Parse(text);
            var raw = doc.RootElement.TryGetProperty("category", out var cat) ? cat.GetString() : null;
            var confidence = doc.RootElement.TryGetProperty("confidence", out var c) ? c.GetDouble() : 0.5;
            var mapped = MapCategory(raw);
            // Ảnh: danh mục chính từ model + các danh mục heuristic thấy trong ghi chú (nếu có).
            var extra = Heuristic(note).Candidates;
            var candidates = new List<string>();
            if (mapped is not null) candidates.Add(mapped);
            candidates.AddRange(extra.Where(x => !candidates.Contains(x)));
            return mapped is not null ? new(mapped, Math.Clamp(confidence, 0, 1), candidates) : new("other", 0.2, candidates);
        }
        catch { return Heuristic(note); }
    }

    /// <summary>Chuẩn hóa category model trả về (có thể là key, nhãn tiếng Việt hoặc đồng nghĩa) về key hợp lệ.</summary>
    private static string? MapCategory(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return null;
        var norm = Normalize(raw);
        if (Allowed.Contains(norm)) return norm;
        return Aliases.TryGetValue(norm, out var mapped) ? mapped : null;
    }

    // Khóa đã bỏ dấu để so khớp với Normalize().
    private static readonly Dictionary<string, string> Aliases = new(StringComparer.Ordinal)
    {
        ["an uong"] = "food", ["do an"] = "food", ["thuc pham"] = "food", ["restaurant"] = "food", ["meal"] = "food", ["drink"] = "food", ["cafe"] = "food", ["coffee"] = "food",
        ["mua sam"] = "shopping", ["do gia dung"] = "shopping", ["electronics"] = "shopping", ["appliance"] = "shopping", ["household"] = "shopping", ["goods"] = "shopping", ["supermarket"] = "shopping",
        ["di lai"] = "transport", ["giao thong"] = "transport", ["taxi"] = "transport", ["fuel"] = "transport", ["gas"] = "transport",
        ["giai tri"] = "entertainment", ["entertainment"] = "entertainment", ["movie"] = "entertainment",
        ["nha o"] = "housing", ["housing"] = "housing", ["rent"] = "housing",
        ["suc khoe"] = "health", ["health"] = "health", ["medical"] = "health", ["medicine"] = "health",
        ["giao duc"] = "education", ["hoc tap"] = "education", ["education"] = "education", ["school"] = "education",
        ["hoa don"] = "bills", ["bills"] = "bills", ["utility"] = "bills", ["utilities"] = "bills", ["electricity"] = "bills", ["water"] = "bills", ["internet"] = "bills", ["dien"] = "bills", ["dien luc"] = "bills", ["tien dien"] = "bills",
        ["khac"] = "other", ["other"] = "other"
    };

    public async Task<AnalysisResult> AnalyzeAsync(string summary)
    {
        var key = ApiKey;
        if (string.IsNullOrWhiteSpace(key)) return FallbackAnalysis(summary);

        try
        {
            var prompt = "Bạn là chuyên gia phân tích chi tiêu cá nhân. Chỉ phân tích dữ liệu được cung cấp, không bịa thêm. "
                + "Trả JSON only với các khóa: summary (string), trends (array of string), anomalies (array of string), recommendations (array of string). Xuất tiếng Việt.\nDữ liệu:\n" + summary;
            var parts = new List<object> { new { text = prompt } };
            var text = await GenerateAsync(key, parts);
            using var doc = JsonDocument.Parse(text);
            return new(
                doc.RootElement.TryGetProperty("summary", out var s) ? s.GetString() ?? "" : "",
                ReadStrings(doc.RootElement, "trends"),
                ReadStrings(doc.RootElement, "anomalies"),
                ReadStrings(doc.RootElement, "recommendations"));
        }
        catch { return FallbackAnalysis(summary); }
    }

    private async Task<string> GenerateAsync(string key, List<object> parts)
    {
        var url = $"{BaseUrl}/models/{Model}:generateContent?key={Uri.EscapeDataString(key)}";
        var body = new
        {
            contents = new object[] { new { parts } },
            generationConfig = new { responseMimeType = "application/json", temperature = 0 }
        };
        var client = httpClientFactory.CreateClient("ai");
        using var response = await client.PostAsync(url, new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json"));
        response.EnsureSuccessStatusCode();
        return await ExtractText(response);
    }

    /// <summary>Lấy text trả về, bỏ qua các part "thinking" của Gemini.</summary>
    private static async Task<string> ExtractText(HttpResponseMessage response)
    {
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var sb = new StringBuilder();
        if (doc.RootElement.TryGetProperty("candidates", out var candidates))
            foreach (var cand in candidates.EnumerateArray())
                if (cand.TryGetProperty("content", out var content) && content.TryGetProperty("parts", out var parts))
                    foreach (var p in parts.EnumerateArray())
                    {
                        if (p.TryGetProperty("thought", out var th) && th.ValueKind == JsonValueKind.True) continue;
                        if (p.TryGetProperty("text", out var t)) sb.Append(t.GetString());
                    }
        return sb.Length == 0 ? "{}" : sb.ToString();
    }

    private static List<string> ReadStrings(JsonElement root, string name) => root.TryGetProperty(name, out var arr) ? arr.EnumerateArray().Select(x => x.GetString() ?? "").Where(x => x.Length > 0).ToList() : [];

    private static readonly (string Cat, double Conf, string[] Keys)[] Rules =
    [
        ("transport", 0.8, ["grab", "taxi", " xe", "xang", "ve xe", "gui xe", "bus", "tau", "may bay", "be "]),
        ("food", 0.82, ["pho", "com", "bun", "mi ", "mi,", "hao hao", "banh", "an ", "do an", "thuc pham", "cafe", "ca phe", "tra sua", "nha hang", "quan an", "lau", "nuong", "buffet", "food"]),
        ("shopping", 0.8, ["mua", "shop", "quan ao", "giay", "dep", "tui", "dien may", "gia dung", "sieu thi", "shopee", "lazada", "tiki", "quat", "tivi", "tu lanh", "zara"]),
        ("entertainment", 0.75, ["phim", "cgv", "game", "karaoke", "nhac", "concert", "du lich", "vui choi", "gym"]),
        ("health", 0.8, ["thuoc", "kham", "benh", "y te", "nha khoa", "suc khoe"]),
        ("education", 0.8, ["hoc", "sach", "khoa hoc", "hoc phi", "truong", "lop ", "gia su", "giao duc"]),
        ("housing", 0.75, ["thue nha", "chung cu", "ky tuc xa", "tien nha"]),
        ("bills", 0.78, ["dien luc", "tien dien", "tien nuoc", "internet", "dien thoai", "cuoc", "truyen hinh", "gas", "hoa don"])
    ];

    /// <summary>Phân loại theo từ khóa (bỏ dấu); trả danh mục chính + tất cả danh mục khớp (cho hóa đơn nhiều loại).</summary>
    private static ClassificationResult Heuristic(string? note)
    {
        var s = Normalize(note);
        if (s.Length == 0) return new("other", 0.2, []);
        var matched = Rules.Where(r => r.Keys.Any(k => s.Contains(k))).ToList();
        if (matched.Count == 0) return new("other", 0.3, []);
        return new(matched[0].Cat, matched[0].Conf, matched.Select(m => m.Cat).ToList());
    }

    /// <summary>Bỏ dấu tiếng Việt + lower để so khớp không phụ thuộc dấu ("Phở" = "Pho").</summary>
    private static string Normalize(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return "";
        var formD = text.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(formD.Length);
        foreach (var ch in formD)
            if (System.Globalization.CharUnicodeInfo.GetUnicodeCategory(ch) != System.Globalization.UnicodeCategory.NonSpacingMark)
                sb.Append(ch);
        return sb.ToString().Normalize(NormalizationForm.FormC).ToLowerInvariant();
    }

    /// <summary>
    /// Phân tích hành vi từ chính số liệu thống kê (không cần quota AI): tổng, trung bình,
    /// nhóm chi nhiều nhất, ngày chi cao bất thường và gợi ý tương ứng.
    /// </summary>
    private static AnalysisResult FallbackAnalysis(string text)
    {
        long total = 0;
        double avg = 0;
        var cats = new List<(string Key, long Amt)>();
        var days = new List<(string Day, long Amt)>();
        foreach (var raw in text.Split('\n'))
        {
            var l = raw.Trim();
            if (l.StartsWith("Total: ") && long.TryParse(l[7..], out var t)) total = t;
            else if (l.StartsWith("Average daily: ") && double.TryParse(l[15..], System.Globalization.CultureInfo.InvariantCulture, out var a)) avg = a;
            else if (l.StartsWith("Category "))
            {
                var p = l[9..].Split(": ");
                if (p.Length == 2 && long.TryParse(p[1], out var amt)) cats.Add((p[0], amt));
            }
            else if (l.StartsWith("Day "))
            {
                var p = l[4..].Split(": ");
                if (p.Length == 2 && long.TryParse(p[1], out var amt)) days.Add((p[0], amt));
            }
        }

        if (total <= 0)
            return new("Chưa có chi tiêu trong khoảng thời gian này.", [], [], ["Thêm chi tiêu để xem phân tích."]);

        var ordered = cats.OrderByDescending(c => c.Amt).ToList();
        var top = ordered[0];
        var share = (int)Math.Round(top.Amt * 100.0 / total);
        var summary = $"Tổng chi {total:N0}đ, trung bình {avg:N0}đ/ngày. Nhóm chi nhiều nhất là {CatName(top.Key)} ({share}%).";

        var trends = new List<string> { $"Nhóm chiếm tỷ trọng cao nhất: {CatName(top.Key)} ({share}%)." };
        if (ordered.Count > 1) trends.Add($"Nhóm tiếp theo: {CatName(ordered[1].Key)} ({ordered[1].Amt:N0}đ).");

        var anomalies = new List<string>();
        if (days.Count > 0)
        {
            var maxDay = days.OrderByDescending(d => d.Amt).First();
            if (avg > 0 && maxDay.Amt > avg * 1.5) anomalies.Add($"Ngày {maxDay.Day} chi {maxDay.Amt:N0}đ, cao hơn mức trung bình.");
        }
        if (share >= 50) anomalies.Add($"{CatName(top.Key)} chiếm hơn một nửa tổng chi ({share}%).");

        var recs = new List<string> { $"Đặt hạn mức cho nhóm {CatName(top.Key)} để cân đối." };
        if (anomalies.Count > 0) recs.Add("Kiểm tra lại các khoản chi bất thường.");

        return new(summary, trends, anomalies, recs);
    }

    private static string CatName(string key) =>
        SnapSpend.Api.Models.CategoryCatalog.All.FirstOrDefault(c => c.Key == key).Name ?? key;
}
