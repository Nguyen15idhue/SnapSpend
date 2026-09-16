using System.Text;
using System.Text.Json;

namespace SnapSpend.Api.Services;

/// <summary>
/// Gọi mini model local qua Ollama (text-only) với timeout ngắn.
/// Lỗi/timeout -> trả null để caller dùng fallback luật.
/// </summary>
public class OllamaService(IConfiguration config, IHttpClientFactory httpClientFactory)
{
    private string BaseUrl => (config["Ollama:BaseUrl"] is { Length: > 0 } b ? b : "http://localhost:11434").TrimEnd('/');
    private string Model => config["Ollama:Model"] is { Length: > 0 } m ? m : "qwen2.5:0.5b";

    public record ReceiptExtract(string? Merchant, string? Date, List<ReceiptItem> Items, long? Total, bool Fallback);
    public record ReceiptItem(string Name, long Amount);

    /// <summary>Cấu trúc hóa text OCR thành JSON {merchant, date, items, total}.</summary>
    public async Task<ReceiptExtract?> StructureReceiptAsync(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return null;
        var prompt = "Đọc văn bản hóa đơn sau và chỉ trả JSON (không giải thích) với đúng các khóa: "
            + "{\"merchant\": string hoặc null, \"date\": \"yyyy-MM-dd\" hoặc null, "
            + "\"items\": [{\"name\": string, \"amount\": số nguyên VNĐ}], \"total\": số nguyên VNĐ hoặc null}. "
            + "Tách từng món (tên hàng + thành tiền) trong bảng, mỗi món một phần tử; "
            + "total là số ở dòng \"Tổng tiền thanh toán\". "
            + "Ví dụ \"Mì 20000, Bún 30000, Tổng 50000\" -> "
            + "{\"merchant\":null,\"date\":null,\"items\":[{\"name\":\"Mì\",\"amount\":20000},{\"name\":\"Bún\",\"amount\":30000}],\"total\":50000}. "
            + "Văn bản:\n" + text;
        var json = await GenerateJsonAsync(prompt);
        if (json is null) return null;
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            var merchant = root.TryGetProperty("merchant", out var m) ? m.GetString() : null;
            var date = root.TryGetProperty("date", out var d) ? d.GetString() : null;
            var items = new List<ReceiptItem>();
            if (root.TryGetProperty("items", out var arr))
                foreach (var it in arr.EnumerateArray())
                    items.Add(new(
                        it.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "",
                        it.TryGetProperty("amount", out var a) && a.TryGetInt64(out var v) ? v : 0));
            long? total = root.TryGetProperty("total", out var t) && t.TryGetInt64(out var tv) ? tv : null;
            return new(merchant, date, items, total, false);
        }
        catch { return null; }
    }

    /// <summary>Viết nhận xét tiếng Việt từ số liệu tổng hợp. Chỉ dùng số liệu cho trước.</summary>
    public async Task<string?> NarrateAnalysisAsync(string summary)
    {
        var prompt = "Bạn là trợ lý chi tiêu. Chỉ dùng số liệu sau, không bịa thêm. "
            + "Viết 2-3 câu tiếng Việt nhận xét: tổng, nhóm chi nhiều nhất, điểm bất thường (nếu có).\nSố liệu:\n" + summary;
        return await GenerateTextAsync(prompt);
    }

    private async Task<string?> GenerateJsonAsync(string prompt)
    {
        var raw = await GenerateTextAsync(prompt, json: true);
        if (raw is null) return null;
        var s = raw.Trim();
        var start = s.IndexOf('{');
        var end = s.LastIndexOf('}');
        return start >= 0 && end > start ? s[start..(end + 1)] : null;
    }

    private async Task<string?> GenerateTextAsync(string prompt, bool json = false)
    {
        try
        {
            var body = new { model = Model, prompt, stream = false, format = json ? "json" : null };
            var client = httpClientFactory.CreateClient("ollama");
            using var res = await client.PostAsync($"{BaseUrl}/api/generate",
                new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json"));
            if (!res.IsSuccessStatusCode) return null;
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
            return doc.RootElement.TryGetProperty("response", out var r) ? r.GetString() : null;
        }
        catch { return null; }
    }
}
