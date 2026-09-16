using SnapSpend.Api.Services;
using Xunit;

namespace SnapSpend.Api.Tests;

/// <summary>Kiểm tra engine phân loại nội bộ với hóa đơn Việt Nam thường gặp.</summary>
public class ClassifyTests
{
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
    public void Heuristic_hoa_don_VN_dung_nhom(string note, string expected)
    {
        Assert.Equal(expected, AiService.ClassifyText(note).Category);
    }
}
