export interface SysFileRecord {
  id: number
  originalName: string
  fileExt: string
  contentType?: string
  fileSize: number
  storageType: string
  accessUrl?: string
  md5?: string
  bizType?: string
  uploaderId?: number
  uploaderName?: string
  downloadUrl: string
  previewUrl: string
  createdAt: string
}

export interface SysAttachmentRecord {
  id: number
  bizType: string
  bizId: string
  fileId: number
  fileName?: string
  contentType?: string
  fileSize?: number
  uploaderName?: string
  downloadUrl?: string
  previewUrl?: string
  sortOrder: number
  remark?: string
  createdAt: string
}
