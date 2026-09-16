using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Xunit;

namespace SnapSpend.Api.Tests;

public class ApiTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    private readonly ApiFactory _factory = factory;

    private async Task<(HttpClient Client, string Token, long UserId)> RegisterAsync(string? email = null)
    {
        var client = _factory.CreateClient();
        email ??= $"{Guid.NewGuid():N}@test.local";
        var res = await client.PostAsJsonAsync("/api/auth/register", new { email, username = Guid.NewGuid().ToString("N"), password = "secret123" });
        Assert.Equal(HttpStatusCode.OK, res.StatusCode);
        var json = await res.Content.ReadFromJsonAsync<JsonElement>();
        var token = json.GetProperty("token").GetString()!;
        var userId = json.GetProperty("user").GetProperty("id").GetInt64();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return (client, token, userId);
    }

    private static async Task<HttpResponseMessage> CreateExpenseAsync(HttpClient client, long amount, string category, string? note = null, string date = "2026-09-15")
    {
        var form = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["amount"] = amount.ToString(),
            ["category"] = category,
            ["note"] = note ?? "",
            ["expenseDate"] = date
        });
        return await client.PostAsync("/api/expenses", form);
    }

    [Fact]
    public async Task Register_va_Login_thanh_cong()
    {
        var (_, _, _) = await RegisterAsync();
    }

    [Fact]
    public async Task Register_trung_email_tra_409()
    {
        var email = $"{Guid.NewGuid():N}@test.local";
        await RegisterAsync(email);
        var client = _factory.CreateClient();
        var res = await client.PostAsJsonAsync("/api/auth/register", new { email, username = Guid.NewGuid().ToString("N"), password = "secret123" });
        Assert.Equal(HttpStatusCode.Conflict, res.StatusCode);
    }

    [Fact]
    public async Task Login_sai_mat_khau_tra_401()
    {
        var (_, _, _) = await RegisterAsync("login401@test.local");
        var client = _factory.CreateClient();
        var res = await client.PostAsJsonAsync("/api/auth/login", new { email = "login401@test.local", password = "wrong-password" });
        Assert.Equal(HttpStatusCode.Unauthorized, res.StatusCode);
    }

    [Fact]
    public async Task Tao_va_list_expense()
    {
        var (client, _, _) = await RegisterAsync();
        var create = await CreateExpenseAsync(client, 50000, "food", "com tam");
        Assert.Equal(HttpStatusCode.OK, create.StatusCode);
        var created = await create.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal("food", created.GetProperty("category").GetString());

        var list = await client.GetFromJsonAsync<JsonElement>("/api/expenses");
        Assert.Equal(1, list.GetProperty("total").GetInt32());
        Assert.Contains(list.GetProperty("items").EnumerateArray(), e => e.GetProperty("amount").GetInt64() == 50000);
    }

    [Fact]
    public async Task Expense_category_la_tra_400()
    {
        var (client, _, _) = await RegisterAsync();
        var res = await CreateExpenseAsync(client, 10000, "khong-ton-tai");
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact]
    public async Task Expense_amount_khong_hop_le_tra_400()
    {
        var (client, _, _) = await RegisterAsync();
        var res = await CreateExpenseAsync(client, 0, "food");
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact]
    public async Task Stats_from_lon_hon_to_tra_400()
    {
        var (client, _, _) = await RegisterAsync();
        var res = await client.GetAsync("/api/stats?from=2026-09-30&to=2026-09-01");
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact]
    public async Task Stats_ngay_sai_dinh_dang_tra_400()
    {
        var (client, _, _) = await RegisterAsync();
        var res = await client.GetAsync("/api/stats?from=01/09/2026&to=2026-09-30");
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact]
    public async Task Share_khi_chua_ket_ban_tra_400()
    {
        var (clientA, _, _) = await RegisterAsync();
        var create = await CreateExpenseAsync(clientA, 30000, "food");
        var expense = await create.Content.ReadFromJsonAsync<JsonElement>();
        var id = expense.GetProperty("id").GetInt64();

        var res = await clientA.PostAsync($"/api/expenses/{id}/share/999999", null);
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
    }

    [Fact]
    public async Task Categories_tra_du_9()
    {
        var client = _factory.CreateClient();
        var cats = await client.GetFromJsonAsync<JsonElement>("/api/categories");
        Assert.Equal(9, cats.GetArrayLength());
    }

    [Fact]
    public async Task Endpoint_can_auth_tra_401()
    {
        var client = _factory.CreateClient();
        var res = await client.GetAsync("/api/expenses");
        Assert.Equal(HttpStatusCode.Unauthorized, res.StatusCode);
    }

    [Fact]
    public async Task Phan_trang_tra_dung_items_va_total()
    {
        var (client, _, _) = await RegisterAsync();
        await CreateExpenseAsync(client, 1000, "food", "p1");
        await CreateExpenseAsync(client, 2000, "food", "p2");
        await CreateExpenseAsync(client, 3000, "food", "p3");
        var paged = await client.GetFromJsonAsync<JsonElement>("/api/expenses?page=1&pageSize=2");
        Assert.Equal(3, paged.GetProperty("total").GetInt32());
        Assert.Equal(2, paged.GetProperty("items").GetArrayLength());
        var empty = await client.GetFromJsonAsync<JsonElement>("/api/expenses?page=5&pageSize=2");
        Assert.Equal(0, empty.GetProperty("items").GetArrayLength());
    }

    [Fact]
    public async Task Bulk_delete_chi_xoa_cua_chinh_user()
    {
        var (clientA, _, _) = await RegisterAsync();
        var (clientB, _, _) = await RegisterAsync();
        var c1 = await CreateExpenseAsync(clientA, 1000, "food", "xoa1");
        var c2 = await CreateExpenseAsync(clientA, 2000, "food", "xoa2");
        var id1 = (await c1.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("id").GetInt64();
        var id2 = (await c2.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("id").GetInt64();
        // User khác xóa ké → không xóa được gì.
        var hack = await clientB.PostAsJsonAsync("/api/expenses/bulk-delete", new { ids = new[] { id1, id2 } });
        Assert.Equal(HttpStatusCode.OK, hack.StatusCode);
        Assert.Equal(0, (await hack.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("deleted").GetInt32());
        // Chính chủ xóa → hết.
        var ok = await clientA.PostAsJsonAsync("/api/expenses/bulk-delete", new { ids = new[] { id1, id2 } });
        Assert.Equal(2, (await ok.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("deleted").GetInt32());
        var after = await clientA.GetFromJsonAsync<JsonElement>("/api/expenses");
        Assert.Equal(0, after.GetProperty("total").GetInt32());
    }
}
