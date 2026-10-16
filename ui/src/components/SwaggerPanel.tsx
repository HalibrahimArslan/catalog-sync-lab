import SwaggerUI from 'swagger-ui-react'
import 'swagger-ui-react/swagger-ui.css'
import type { OpenApiDoc } from '../api/types'

/**
 * Swagger UI (büyük paket): ApiView bunu lazy yükler, sekme açılmadıkça indirilmez.
 * Swagger UI'ın koyu teması yok; iki temada da açık zeminde durur.
 */
export default function SwaggerPanel({ spec }: { spec: OpenApiDoc }) {
  return (
    <div className="overflow-hidden rounded-2xl border border-line bg-[#fdfdfc] py-2 text-[#3b4151] shadow-card [color-scheme:light]">
      <SwaggerUI spec={spec} docExpansion="list" defaultModelsExpandDepth={-1} tryItOutEnabled displayRequestDuration />
    </div>
  )
}
