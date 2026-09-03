using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace SnapSpend.Api.Services;

public record ClassificationResult(string Category, double Confidence);
public record AnalysisResult(string Summary, List<string> Trends, List<string> Anomalies, List<string> Recommendations);

public class AiService(IConfiguration config, IHttpClientFactory httpClientFactory)
{
    private static readonly string[] Allowed = ["food", "shopping", "transport", "entertainment", "housing", "health", "education", "bills", "other"];

    public async Task<ClassificationResult> ClassifyAsync(string? imagePath, string? note)
    {
        var key = config["OpenAI:ApiKey"];
        if (string.IsNullOrWhiteSpace(key) || string.IsNullOrWhiteSpace(imagePath) || !File.Exists(imagePath))
            return Heuristic(note);

        var bytes = await File.ReadAllBytesAsync(imagePath);
        var mime = Path.GetExtension(imagePath).ToLowerInvariant() switch { ".png" => "image/png", ".webp" => "image/webp", _ => "image/jpeg" };
        var dataUrl = $"data:{mime};base64,{Convert.ToBase64String(bytes)}";
        var body = new
        {
            model = config["OpenAI:Model"] ?? "gpt-5.6-luna",
            input = new[] { new {
                role = "user",
                content = new object[] {
                    new { type = "input_text", text = """Classify this expense into exactly one category: food, shopping, transport, entertainment, housing, health, education, bills, other. Return JSON only: {"category":"food","confidence":0.0}.""" },
                    new { type = "input_image", image_url = dataUrl }
                }
            }}
        };
        var client = httpClientFactory.CreateClient("openai");
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", key);
        using var response = await client.PostAsync("https://api.openai.com/v1/responses", new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json"));
        if (!response.IsSuccessStatusCode) return Heuristic(note);
        var text = await ExtractOutputText(response);
        try
        {
            using var doc = JsonDocument.Parse(text);
            var category = doc.RootElement.GetProperty("category").GetString() ?? "other";
            var confidence = doc.RootElement.GetProperty("confidence").GetDouble();
            return Allowed.Contains(category) ? new(category, Math.Clamp(confidence, 0, 1)) : new("other", 0.2);
        }
        catch { return Heuristic(note); }
    }

    public async Task<AnalysisResult> AnalyzeAsync(string summary)
    {
        var key = config["OpenAI:ApiKey"];
        if (string.IsNullOrWhiteSpace(key)) return FallbackAnalysis(summary);
        var body = new
        {
            model = config["OpenAI:Model"] ?? "gpt-5.6-luna",
            input = "You are a personal spending analyst. Do not invent facts. Analyze only the supplied aggregated data. Return JSON only with keys summary (string), trends (array of strings), anomalies (array), recommendations (array). Vietnamese output. Data:\n" + summary
        };
        var client = httpClientFactory.CreateClient("openai");
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", key);
        using var response = await client.PostAsync("https://api.openai.com/v1/responses", new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json"));
        if (!response.IsSuccessStatusCode) return FallbackAnalysis(summary);
        var text = await ExtractOutputText(response);
        try {
            using var doc = JsonDocument.Parse(text);
            return new(
                doc.RootElement.GetProperty("summary").GetString() ?? "",
                ReadStrings(doc.RootElement, "trends"), ReadStrings(doc.RootElement, "anomalies"), ReadStrings(doc.RootElement, "recommendations"));
        } catch { return FallbackAnalysis(summary); }
    }

    private static async Task<string> ExtractOutputText(HttpResponseMessage response)
    {
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        if (doc.RootElement.TryGetProperty("output_text", out var direct)) return direct.GetString() ?? "{}";
        var sb = new StringBuilder();
        if (doc.RootElement.TryGetProperty("output", out var output))
            foreach (var item in output.EnumerateArray())
                if (item.TryGetProperty("content", out var content))
                    foreach (var c in content.EnumerateArray())
                        if (c.TryGetProperty("text", out var t)) sb.Append(t.GetString());
        return sb.Length == 0 ? "{}" : sb.ToString();
    }

    private static List<string> ReadStrings(JsonElement root, string name) => root.TryGetProperty(name, out var arr) ? arr.EnumerateArray().Select(x => x.GetString() ?? "").Where(x => x.Length > 0).ToList() : [];

    private static ClassificationResult Heuristic(string? note)
    {
        var s = (note ?? "").ToLowerInvariant();
        if (s.Contains("grab") || s.Contains("taxi") || s.Contains("be") || s.Contains("xe")) return new("transport", 0.55);
        if (s.Contains("phở") || s.Contains("food") || s.Contains("ăn") || s.Contains("cơm") || s.Contains("restaurant")) return new("food", 0.65);
        if (s.Contains("zara") || s.Contains("shop") || s.Contains("mua") || s.Contains("quần áo")) return new("shopping", 0.6);
        return new("other", 0.2);
    }

    private static AnalysisResult FallbackAnalysis(string text) => new("Đây là phân tích tự động dựa trên số liệu thống kê hiện có.", ["Theo dõi nhóm chi tiêu có tỷ trọng cao nhất."], [], ["So sánh tuần này với tuần trước trước khi đặt mục tiêu mới."]);
}
