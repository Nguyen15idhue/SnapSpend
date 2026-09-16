using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
using SnapSpend.Api.Services;
using Xunit;

namespace SnapSpend.Api.Tests;

/// <summary>Kiểm tra engine nhận diện đọc từ DB: thang dòng Tổng, tách món, đối chiếu, phân loại, tóm tắt.</summary>
public class RecognitionTests
{
    private static async Task<RecognitionService> CreateServiceAsync()
    {
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase($"recog-{Guid.NewGuid():N}").Options;
        var db = new AppDbContext(options);
        await db.Database.EnsureCreatedAsync();
        return new RecognitionService(db);
    }

    // --- Thang dòng Tổng: ưu tiên + dòng cuối + loại trừ ---

    [Theory]
    [InlineData("Ghế hấp 2,1 1.050.000 2.205.000\nTổng tiền trước thuế: 5.825.000\nTổng thuế GTGT: 582.500\nTổng tiền thanh toán: 6.407.500", 6407500L)] // bill12
    [InlineData("Trà Lipton 1 27.000 27.000\nTổng cộng Sub total 281.000\nPhí phục vụ 5% 14.050\nVAT 10% 29.505\nThành tiền Total 324.555", 324555L)] // bill4
    [InlineData("Baba nướng 3 340.000 1.020.000\nThành tiền: 3.258.000\nPhụ thu: 325.800\nTổng cộng: 3.584.000", 3584000L)] // bill6
    [InlineData("Bia Heniken 2 x 20.000 40.000\nTổng dịch vụ: 125.000\nTổng tiền giờ: 11.000\nTổng hóa đơn: 136.000", 136000L)] // bill8
    [InlineData("Coca 2 25.000 50.000\nT.Cộng 9 225,000\nTIỀN MẶT 225,000", 225000L)] // bill13
    [InlineData("Heineken lon 96 26.000 2.496.000\nTONG CONG 7751.000", 7751000L)] // bill5
    [InlineData("CUA GACH 1 980.000 980.000\nTổng thanh toán 1.795.000\nCòn phải thu 1.795.000", 1795000L)] // bill2
    [InlineData("Bánh Sandwich 15.000 x2 30.000\nTổng 5SP 260.000", 260000L)] // bill9
    [InlineData("Cơm tấm 1 17.000 17.000\nTổng: 54,000", 54000L)] // bill3
    [InlineData("Ốc Hương 0.5 550,000 275,000\nTổng thành tiền 1,550,000", 1550000L)] // bill10
    public async Task ExtractTotal_dung_dong_quyet_toan(string text, long expected)
    {
        var svc = await CreateServiceAsync();
        Assert.Equal(expected, await svc.ExtractTotalAsync(text));
    }

    [Fact]
    public async Task ExtractTotal_null_khi_chi_co_tien_mat()
    {
        var svc = await CreateServiceAsync();
        Assert.Null(await svc.ExtractTotalAsync("TIỀN MẶT 1,053,000"));
    }

    [Fact]
    public async Task ExtractTotal_bo_dong_ma_so_hieu()
    {
        var svc = await CreateServiceAsync();
        Assert.Null(await svc.ExtractTotalAsync("Số (No): 7363\nMã CQT: M2-26\nNgày 05/07/2026"));
    }

    // --- Tách món: số cuối dòng + giữ dòng trùng ---

    [Fact]
    public async Task ParseItems_so_cuoi_dong()
    {
        var svc = await CreateServiceAsync();
        var items = await svc.ParseItemsAsync("Mì tôm 2 25.000 50.000");
        var item = Assert.Single(items);
        Assert.Equal(50000L, item.Amount);
    }

    [Fact]
    public async Task ParseItems_giu_dong_trung_ten()
    {
        var svc = await CreateServiceAsync();
        var text = "Coca 2 25.000 50.000\nSprite 2 25.000 50.000\nCoca 2 25.000 50.000\nTonic 2 25.000 50.000\nSoda 1 25.000 25.000";
        var items = await svc.ParseItemsAsync(text);
        Assert.Equal(5, items.Count);
        Assert.Equal(225000L, items.Sum(i => i.Amount));
    }

    // --- Đối chiếu + thang tin cậy ---

    [Theory]
    [InlineData("Tiền điện tháng 9", "bills")] // đ→d: "tien dien" phải khớp
    [InlineData("Đi chợ 200k", "food")] // "di cho" phải khớp
    [InlineData("Địa chỉ: 129 Huỳnh Tấn Phát", "other")] // dòng nhiễu có đ phải bị loại êm (không crash/khớp bậy)
    [InlineData("Đồng phục Zara 500k", "shopping")] // "dong phuc" + merchant Zara
    [InlineData("Dự lịch Đà Lạt 2tr", "entertainment")] // "du lich" phải khớp
    public async Task ClassifyText_chu_d_co_dau(string note, string expected)
    {
        var svc = await CreateServiceAsync();
        Assert.Equal(expected, (await svc.ClassifyTextAsync(note)).Category);
    }

    [Fact]
    public async Task ParseItems_bo_dong_nhieu_co_dau()
    {
        var svc = await CreateServiceAsync();
        Assert.Empty(await svc.ParseItemsAsync("Khách đã trả 260.000 đ"));
        Assert.Empty(await svc.ParseItemsAsync("Phụ thu: 325.800"));
        Assert.Empty(await svc.ParseItemsAsync("Tiền hàng 25.000"));
    }

    [Fact]
    public async Task Debug_fixture()
    {
        var svc = await CreateServiceAsync();
        var text = File.ReadAllText(Path.Combine(DocsRoot(), "ocr", "bill1.jpg.txt"));
        var items = await svc.ParseItemsAsync(text);
        throw new Xunit.Sdk.XunitException("ITEMS(" + items.Count + "): " + string.Join(" | ", items.Select(i => $"{i.Name}={i.Amount}")));
    }

    private static string DocsRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !Directory.Exists(Path.Combine(dir.FullName, "docs", "3")))
            dir = dir.Parent;
        return Path.Combine(dir!.FullName, "docs", "3");
    }

    [Fact]
    public void ValidateTotal_nguong_5_phan_tram()
    {
        Assert.True(RecognitionService.ValidateTotal(100000L, [new("a", 95000L)]));
        Assert.False(RecognitionService.ValidateTotal(100000L, [new("a", 94000L)]));
        Assert.False(RecognitionService.ValidateTotal(0L, [new("a", 1000L)]));
        Assert.False(RecognitionService.ValidateTotal(100000L, []));
    }

    [Fact]
    public async Task GuessAmount_high_medium_low()
    {
        var svc = await CreateServiceAsync();
        var hi = await svc.GuessAmountAsync("Mì tôm 1 20.000 20.000\nBún bò 1 30.000 30.000\nTổng thanh toán 50.000");
        Assert.Equal((50000L, "HIGH"), hi);
        var med = await svc.GuessAmountAsync("Trà 1 27.000 27.000\nPizza 1 65.000 65.000\nTổng cộng 92.000\nThành tiền Total 105.800");
        Assert.Equal((105800L, "MEDIUM"), med);
        var sumOnly = await svc.GuessAmountAsync("Mì tôm 1 5.000 20.000\nBún bò 1 30.000 30.000");
        Assert.Equal((50000L, "MEDIUM"), sumOnly);
        var low = await svc.GuessAmountAsync("12345678");
        Assert.Equal((12345678L, "LOW"), low);
        var none = await svc.GuessAmountAsync("   ");
        Assert.Equal((null, "LOW"), none);
    }

    // --- Phân loại: dài nhất thắng, hòa thì merchant thắng ---

    [Theory]
    [InlineData("GrabFood 85k", "food")]
    [InlineData("Highlands 2 tra dao", "food")]
    [InlineData("Di cho 200k", "food")]
    [InlineData("Tap hoa co Hai", "food")]
    [InlineData("Mua ve xem phim CGV", "entertainment")]
    [InlineData("Ve so dai Dong Nai", "entertainment")]
    [InlineData("Lam dep spa 500k", "entertainment")]
    [InlineData("Pharmacity mua thuoc", "health")]
    [InlineData("Kham benh Medlatec", "health")]
    [InlineData("EVN tien dien thang 9", "bills")]
    [InlineData("Nap tien Viettel", "bills")]
    [InlineData("Nuoc sach 120k", "bills")]
    [InlineData("Cho thue nha thang 9", "housing")]
    [InlineData("Thay lop xe 300k", "transport")]
    [InlineData("GrabBike di lam", "transport")]
    [InlineData("Ve may bay Vietjet", "transport")]
    [InlineData("Dong hoc phi IELTS", "education")]
    [InlineData("Ao thun Zara", "shopping")]
    [InlineData("Dien may Xanh mua quat", "shopping")]
    [InlineData("Bida Phuong Hoang gio 13 phut", "entertainment")] // bill8: merchant thang "bia"
    [InlineData("Bia Heniken 2 x 20.000", "food")]
    [InlineData("HAI SAN BON BAO lau thai", "food")]
    [InlineData("QUAN NHAU BINH DAN tiger nau", "food")]
    public async Task ClassifyText_dung_nhom(string note, string expected)
    {
        var svc = await CreateServiceAsync();
        Assert.Equal(expected, (await svc.ClassifyTextAsync(note)).Category);
    }

    [Fact]
    public async Task ClassifyText_rong_va_la()
    {
        var svc = await CreateServiceAsync();
        Assert.Equal("other", (await svc.ClassifyTextAsync("")).Category);
        Assert.Equal("other", (await svc.ClassifyTextAsync("xyz qwerty")).Category);
    }

    [Fact]
    public async Task ClassifyText_hoa_don_nhieu_loai_tra_candidates()
    {
        var svc = await CreateServiceAsync();
        var r = await svc.ClassifyTextAsync("cơm tấm và grab");
        Assert.Contains("food", r.Candidates);
        Assert.Contains("transport", r.Candidates);
    }

    [Fact]
    public async Task MapCategory_nhan_tieng_viet_ve_key()
    {
        var svc = await CreateServiceAsync();
        Assert.Equal("food", await svc.MapCategoryAsync("Ăn uống"));
        Assert.Equal("food", await svc.MapCategoryAsync("food"));
        Assert.Null(await svc.MapCategoryAsync("zzz"));
        Assert.Null(await svc.MapCategoryAsync(null));
    }

    // --- Tóm tắt: sạch MST/mã, giữ tên món ---

    [Fact]
    public async Task Summarize_bo_boilerplate_giu_mon()
    {
        var svc = await CreateServiceAsync();
        var s = await svc.SummarizeAsync("HÓA ĐƠN BÁN HÀNG\nMã số thuế: 0108892073\nMì Hảo Hảo Tôm Chua Cay 30 Gói 4 5.000 20.000\nTổng tiền thanh toán: 50.000");
        Assert.Contains("Hảo Hảo", s);
        Assert.DoesNotContain("mã số thuế", s.ToLowerInvariant());
    }

    // --- Parse tổng hợp: đúng 3 trường lõi ---

    [Fact]
    public async Task Parse_du_tong_muc_items_category_summary()
    {
        var svc = await CreateServiceAsync();
        var r = await svc.ParseAsync("Phở bò 1 30.000 30.000\nTổng thanh toán 30.000");
        Assert.Equal(30000L, r.Total);
        Assert.Equal("HIGH", r.Level);
        Assert.Equal("food", r.Category);
        Assert.NotEmpty(r.Items);
        Assert.NotEqual("", r.Summary);
    }

    [Fact]
    public async Task Parse_bill8_merchant_thang()
    {
        var svc = await CreateServiceAsync();
        var r = await svc.ParseAsync("BIDA\nBida Phượng Hoàng\nBia Heniken 2 x 20.000 40.000\nThuốc lá 1 x 25.000 25.000\nTổng dịch vụ: 125.000\nTổng tiền giờ: 11.000\nTổng hóa đơn: 136.000");
        Assert.Equal(136000L, r.Total);
        Assert.Equal("entertainment", r.Category);
    }
}
