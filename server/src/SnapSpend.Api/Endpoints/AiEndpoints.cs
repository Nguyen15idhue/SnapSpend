using System.Security.Claims;
using SnapSpend.Api.Services;

namespace SnapSpend.Api.Endpoints;

/// <summary>
/// Endpoint nhận diện: engine nội bộ từ DB (parse) + OpenRouter kiểm lại khi engine bó tay.
/// </summary>
public static class AiEndpoints
{
    public static IEndpointRouteBuilder MapAi(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/ai").RequireAuthorization();

        // Parse text OCR bằng engine DB: {total, level, items, category, confidence, summary}.
        g.MapPost("/parse", async (ParseRequest req, ClaimsPrincipal p, RecognitionService recognition) => {
            _ = p;
            if (string.IsNullOrWhiteSpace(req.Text)) return Results.BadRequest(new { message = "Text is required." });
            if (req.Text.Length > 4000) return Results.BadRequest(new { message = "Text too long (max 4000)." });
            var r = await recognition.ParseAsync(req.Text);
            return Results.Ok(new {
                total = r.Total,
                level = r.Level,
                items = r.Items.Select(i => new { name = i.Name, amount = i.Amount }).ToList(),
                category = r.Category,
                confidence = r.Confidence,
                summary = r.Summary
            });
        });

        // OpenRouter xác minh tổng khi engine bó tay. Thiếu key/lỗi -> confident=false, không 500.
        g.MapPost("/verify-total", async (VerifyRequest req, ClaimsPrincipal p, OpenRouterService openRouter) => {
            _ = p;
            if (string.IsNullOrWhiteSpace(req.OcrText)) return Results.BadRequest(new { message = "OcrText is required." });
            if (req.OcrText.Length > 4000) return Results.BadRequest(new { message = "OcrText too long (max 4000)." });
            var total = await openRouter.VerifyTotalAsync(req.OcrText);
            if (total is null) return Results.Ok(new VerifyResult(null, false, "unavailable"));
            return Results.Ok(new VerifyResult(total, true, "ok"));
        });

        return app;
    }

    public record ParseRequest(string Text);
    public record VerifyRequest(string OcrText, long? EngineTotal);
    public record VerifyResult(long? Total, bool Confident, string Reason);
}
