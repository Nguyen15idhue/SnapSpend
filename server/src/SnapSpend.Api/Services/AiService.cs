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
public class AiService(IConfiguration config, IHttpClientFactory httpClientFactory, OpenRouterService openRouter, RecognitionService recognition)
{
    private string? ApiKey => config["Ai:ApiKey"];
    private string Model => config["Ai:Model"] is { Length: > 0 } m ? m : "gemini-3.8-flash";
    private string BaseUrl => (config["Ai:BaseUrl"] is { Length: > 0 } b ? b : "https://generativelanguage.googleapis.com/v1beta").TrimEnd('/');

    public async Task<ClassificationResult> ClassifyAsync(string? imagePath, string? note)
    {
        if (!string.IsNullOrWhiteSpace(note)) return await recognition.ClassifyTextAsync(note);
        if (string.IsNullOrWhiteSpace(imagePath) || !File.Exists(imagePath)) return await recognition.ClassifyTextAsync(note);
        var bytes = await File.ReadAllBytesAsync(imagePath);
        var mime = Path.GetExtension(imagePath).ToLowerInvariant() switch { ".png" => "image/png", ".webp" => "image/webp", _ => "image/jpeg" };
        return await ClassifyAsync(bytes, mime, note);
    }

    /// <summary>
    /// Phân loại: có ghi chú -> engine nội bộ từ DB (tức thời, không phụ thuộc mạng/quota);
    /// không có ghi chú -> thử Gemini theo ảnh (timeout ngắn), lỗi thì fallback.
    /// </summary>
    public async Task<ClassificationResult> ClassifyAsync(byte[]? image, string? mime, string? note)
    {
        if (!string.IsNullOrWhiteSpace(note)) return await recognition.ClassifyTextAsync(note);

        var key = ApiKey;
        if (string.IsNullOrWhiteSpace(key) || image is not { Length: > 0 }) return await recognition.ClassifyTextAsync(note);

        try
        {
            var parts = new List<object>
            {
                new { text = "Bạn phân loại một ảnh hóa đơn/chi tiêu tiếng Việt (có thể là ảnh chụp màn hình dọc — hãy đọc toàn bộ từ trên xuống). Ưu tiên TÊN CỬA HÀNG/thương hiệu rồi mới đến món lẻ, và chọn ĐÚNG MỘT key sau:\n"
                    + "- food: ăn uống, nhà hàng, quán ăn, mì, bún, cơm, cà phê, trà sữa, Highlands, Phúc Long, KFC, đồ ăn giao tận nơi (GrabFood/ShopeeFood), đi chợ, tạp hóa, Bách Hóa Xanh, WinMart, Circle K, siêu thị mini\n"
                    + "- shopping: mua sắm đồ vật, quần áo, điện máy (Điện Máy Xanh, Thế Giới Di Động, FPT Shop), đồ gia dụng, siêu thị lớn, Shopee/Lazada/Tiki, mỹ phẩm, nhà sách\n"
                    + "- transport: đi lại, xăng, taxi, Grab (GrabBike/GrabCar — không phải GrabFood), vé xe, vé máy bay, hãng bay, gửi xe, sửa xe\n"
                    + "- entertainment: giải trí, phim/CGV, game, karaoke, du lịch, khách sạn, vé số, làm đẹp/spa\n"
                    + "- housing: tiền nhà, thuê nhà/trọ, chung cư, phí quản lý, sửa nhà\n"
                    + "- health: sức khỏe, thuốc, nhà thuốc (Pharmacity/Long Châu), khám bệnh, bệnh viện, xét nghiệm\n"
                    + "- education: giáo dục, học phí, sách vở, khóa học, luyện thi (IELTS/TOEIC), dụng cụ học tập\n"
                    + "- bills: hóa đơn điện (EVN), nước sạch, internet (VNPT/Viettel/FPT), cước điện thoại, nạp tiền, ví điện tử, chuyển khoản thanh toán\n"
                    + "- other: không thuộc nhóm nào\n"
                    + "Chỉ trả JSON: {\"category\":\"food\",\"confidence\":0.0}" },
                new { inline_data = new { mime_type = string.IsNullOrWhiteSpace(mime) ? "image/jpeg" : mime, data = Convert.ToBase64String(image) } }
            };
            var text = await GenerateAsync(key, parts);
            using var doc = JsonDocument.Parse(text);
            var raw = doc.RootElement.TryGetProperty("category", out var cat) ? cat.GetString() : null;
            var confidence = doc.RootElement.TryGetProperty("confidence", out var c) ? c.GetDouble() : 0.5;
            var mapped = await recognition.MapCategoryAsync(raw);
            // Ảnh: danh mục chính từ model + các danh mục engine thấy trong ghi chú (nếu có).
            var extra = (await recognition.ClassifyTextAsync(note)).Candidates;
            var candidates = new List<string>();
            if (mapped is not null) candidates.Add(mapped);
            candidates.AddRange(extra.Where(x => !candidates.Contains(x)));
            return mapped is not null ? new(mapped, Math.Clamp(confidence, 0, 1), candidates) : new("other", 0.2, candidates);
        }
        catch { return await recognition.ClassifyTextAsync(note); }
    }

    /// <summary>Phân loại text thuần bằng engine nội bộ từ DB (không cần key/quota).</summary>
    public Task<ClassificationResult> ClassifyText(string? note) => recognition.ClassifyTextAsync(note);

    public async Task<AnalysisResult> AnalyzeAsync(string summary)
    {
        // Luôn tính từ số liệu (chuẩn, nhanh, không quota).
        await Task.CompletedTask;
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

    /// <summary>AI đánh giá + lời khuyên từ số liệu (OpenRouter free); null khi lỗi/hết quota.</summary>
    public Task<string?> EvaluateAsync(string summary) => openRouter.NarrateAnalysisAsync(summary);

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

    /// <summary>
    /// Phân tích hành vi từ chính số liệu thống kê (không cần quota AI): tổng, trung bình,
    /// nhóm chi nhiều nhất, ngày chi cao bất thường và gợi ý tương ứng.
    /// </summary>
    private static AnalysisResult FallbackAnalysis(string text)
    {
        long total = 0;
        double avg = 0;
        long prevTotal = -1;
        var cats = new List<(string Key, long Amt)>();
        var days = new List<(string Day, long Amt)>();
        foreach (var raw in text.Split('\n'))
        {
            var l = raw.Trim();
            if (l.StartsWith("Total: ") && long.TryParse(l[7..], out var t)) total = t;
            else if (l.StartsWith("Previous total: ") && long.TryParse(l[16..], out var pt)) prevTotal = pt;
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
        if (prevTotal >= 0)
        {
            var diff = total - prevTotal;
            var pct = prevTotal > 0 ? (int)Math.Round(diff * 100.0 / prevTotal) : 100;
            trends.Add(diff >= 0
                ? $"Tăng {pct}% so với kỳ trước ({prevTotal:N0}đ)."
                : $"Giảm {Math.Abs(pct)}% so với kỳ trước ({prevTotal:N0}đ).");
        }

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
