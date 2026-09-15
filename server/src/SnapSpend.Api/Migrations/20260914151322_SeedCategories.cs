using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace SnapSpend.Api.Migrations
{
    /// <inheritdoc />
    public partial class SeedCategories : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.InsertData(
                table: "categories",
                columns: new[] { "key", "emoji", "name" },
                values: new object[,]
                {
                    { "bills", "🧾", "Hóa đơn" },
                    { "education", "📚", "Học tập" },
                    { "entertainment", "🎬", "Giải trí" },
                    { "food", "🍜", "Ăn uống" },
                    { "health", "💊", "Sức khỏe" },
                    { "housing", "🏠", "Nhà ở" },
                    { "other", "•", "Khác" },
                    { "shopping", "🛍️", "Shopping" },
                    { "transport", "🛵", "Đi lại" }
                });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "bills");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "education");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "entertainment");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "food");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "health");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "housing");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "other");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "shopping");

            migrationBuilder.DeleteData(
                table: "categories",
                keyColumn: "key",
                keyValue: "transport");
        }
    }
}
