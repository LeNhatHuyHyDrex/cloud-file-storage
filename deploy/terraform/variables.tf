variable "project" {
  type    = string
  default = "nimbus"
}

variable "region" {
  type    = string
  default = "ap-southeast-1" # Singapore - gan Viet Nam
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "disk_gb" {
  type    = number
  default = 20
}

variable "key_name" {
  description = "Ten EC2 key pair da tao san (dung cho SSH / GitHub Actions)"
  type        = string
}

variable "ssh_cidrs" {
  description = "Dai IP duoc SSH. Nen thu hep lai IP cua ban; GitHub Actions can 0.0.0.0/0 hoac self-hosted runner"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "domain" {
  description = "Ten mien tro ve Elastic IP (de Caddy xin HTTPS). De trong = chi dung HTTP qua IP"
  type        = string
  default     = ""
}

variable "app_image" {
  description = "Image cua ung dung tren GHCR, vd ghcr.io/<owner>/<repo>:latest"
  type        = string
}

variable "force_destroy_bucket" {
  description = "Cho phep terraform destroy xoa bucket con du lieu (chi bat khi demo)"
  type        = bool
  default     = false
}
