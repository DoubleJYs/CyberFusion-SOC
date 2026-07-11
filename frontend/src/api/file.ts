import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { SysAttachmentRecord, SysFileRecord } from '@/types/file'

export interface FileQuery extends PageQuery {
  bizType?: string
}

export interface AttachmentCreateForm {
  bizType: string
  bizId: string
  fileId: number
  sortOrder?: number
  remark?: string
}

export interface FileTablePreview {
  fileId: number
  originalName: string
  format: string
  headers: string[]
  rows: string[][]
  totalRows: number
  truncated: boolean
}

export async function uploadFile(file: File, bizType?: string, onProgress?: (percent: number) => void): Promise<SysFileRecord> {
  const formData = new FormData()
  formData.append('file', file)
  if (bizType) formData.append('bizType', bizType)
  const response = await request.post<ApiResult<SysFileRecord>>('/system/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (event.total && onProgress) onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data.data
}

export async function fetchFiles(params: FileQuery): Promise<PageResult<SysFileRecord>> {
  const response = await request.get<ApiResult<PageResult<SysFileRecord>>>('/system/files', { params })
  return response.data.data
}

export async function fetchFileDetail(id: number): Promise<SysFileRecord> {
  const response = await request.get<ApiResult<SysFileRecord>>(`/system/files/${id}`)
  return response.data.data
}

export async function deleteFile(id: number) {
  await request.delete(`/system/files/${id}`)
}

export async function downloadFileBlob(id: number): Promise<Blob> {
  const response = await request.get(`/system/files/${id}/download`, { responseType: 'blob' })
  return response.data
}

export async function previewFileBlob(id: number): Promise<Blob> {
  const response = await request.get(`/system/files/${id}/preview`, { responseType: 'blob' })
  return response.data
}

export async function previewFileTable(id: number): Promise<FileTablePreview> {
  const response = await request.get<ApiResult<FileTablePreview>>(`/system/files/${id}/table-preview`)
  return response.data.data
}

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export async function createAttachment(data: AttachmentCreateForm): Promise<SysAttachmentRecord> {
  const response = await request.post<ApiResult<SysAttachmentRecord>>('/system/attachments', data)
  return response.data.data
}

export async function fetchAttachments(params: { bizType?: string; bizId?: string }): Promise<SysAttachmentRecord[]> {
  const response = await request.get<ApiResult<SysAttachmentRecord[]>>('/system/attachments', { params })
  return response.data.data
}

export async function deleteAttachment(id: number) {
  await request.delete(`/system/attachments/${id}`)
}
