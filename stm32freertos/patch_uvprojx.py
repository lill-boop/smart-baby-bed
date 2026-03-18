import re

proj_path = 'MDK-ARM/Project_C8.uvprojx'
with open(proj_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update <IncludePath>
include_patch = r';../FreeRTOS/include;../FreeRTOS/portable/RVDS/ARM_CM3'
if 'FreeRTOS/include' not in content:
    content = re.sub(
        r'(<IncludePath>)(.*?)(</IncludePath>)',
        r'\g<1>\g<2>' + include_patch + r'\g<3>',
        content
    )

# 2. Add Groups
groups_patch = """
        <Group>
          <GroupName>FreeRTOS_CORE</GroupName>
          <Files>
            <File>
              <FileName>croutine.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/croutine.c</FilePath>
            </File>
            <File>
              <FileName>event_groups.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/event_groups.c</FilePath>
            </File>
            <File>
              <FileName>list.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/list.c</FilePath>
            </File>
            <File>
              <FileName>queue.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/queue.c</FilePath>
            </File>
            <File>
              <FileName>stream_buffer.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/stream_buffer.c</FilePath>
            </File>
            <File>
              <FileName>tasks.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/tasks.c</FilePath>
            </File>
            <File>
              <FileName>timers.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/timers.c</FilePath>
            </File>
          </Files>
        </Group>
        <Group>
          <GroupName>FreeRTOS_PORT</GroupName>
          <Files>
            <File>
              <FileName>heap_4.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/portable/MemMang/heap_4.c</FilePath>
            </File>
            <File>
              <FileName>port.c</FileName>
              <FileType>1</FileType>
              <FilePath>../FreeRTOS/portable/RVDS/ARM_CM3/port.c</FilePath>
            </File>
          </Files>
        </Group>
"""

if 'FreeRTOS_CORE' not in content:
    content = re.sub(
        r'(</Groups>)',
        groups_patch + r'\g<1>',
        content
    )

with open(proj_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Keil Project Successfully Patched!")
