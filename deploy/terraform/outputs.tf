output "public_ip" {
  description = "Dat secret DEPLOY_HOST = gia tri nay; tro ban ghi DNS A cua domain ve day"
  value       = aws_eip.app.public_ip
}

output "bucket" {
  value = aws_s3_bucket.files.bucket
}

output "app_url" {
  value = var.domain != "" ? "https://${var.domain}" : "http://${aws_eip.app.public_ip}"
}

output "ssh" {
  value = "ssh ubuntu@${aws_eip.app.public_ip}"
}
