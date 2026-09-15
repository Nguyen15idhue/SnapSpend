using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SnapSpend.Api.Migrations
{
    /// <inheritdoc />
    public partial class UpdateCategoryNames : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.UpdateData(
                table: "categories",
                keyColumn: "key",
                keyValue: "education",
                column: "name",
                value: "Giáo dục");

            migrationBuilder.UpdateData(
                table: "categories",
                keyColumn: "key",
                keyValue: "shopping",
                column: "name",
                value: "Mua sắm");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.UpdateData(
                table: "categories",
                keyColumn: "key",
                keyValue: "education",
                column: "name",
                value: "Học tập");

            migrationBuilder.UpdateData(
                table: "categories",
                keyColumn: "key",
                keyValue: "shopping",
                column: "name",
                value: "Shopping");
        }
    }
}
