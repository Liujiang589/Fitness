"""Render original Kotlin motion-engine report frames for pose review, not device screenshots.

No external artwork. Run :app:testDebugUnitTest first to export the engine's actual poses.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BG = (237, 240, 233)
BODY = (198, 207, 191)
EDGE = (132, 145, 124)
ACTIVE = (216, 117, 84)


def add(a, b): return [a[i] + b[i] for i in range(3)]
def sub(a, b): return [a[i] - b[i] for i in range(3)]
def mul(a, value): return [n * value for n in a]
def lerp(a, b, t): return add(a, mul(sub(b, a), t))


def render(frame, guide, width=350, height=220):
    image = Image.new('RGB', (width, height), BG)
    draw = ImageDraw.Draw(image)
    scale = min(width / 3.05, height / 2.62)
    yaw = math.radians(32)
    def project(v):
        x, y, z = v
        depth = -x * math.sin(yaw) + z * math.cos(yaw)
        return (width * .49 + (x * math.cos(yaw) + z * math.sin(yaw)) * scale,
                height * .93 - (y - depth * .10) * scale)
    def depth(v): return -v[0] * math.sin(yaw) + v[2] * math.cos(yaw)
    def circle(center, radius, color):
        x, y = center
        draw.ellipse((x-radius,y-radius,x+radius,y+radius),fill=color)
    def rod(a, b, thickness, color, outline=True):
        a, b = project(a), project(b)
        w = max(1, round(thickness * scale))
        if outline:
            dark = tuple(round(n*.7) for n in color)
            draw.line((a,b),fill=dark,width=w+2)
            circle(a,(w+2)/2,dark);circle(b,(w+2)/2,dark)
        draw.line((a,b),fill=color,width=w)
        circle(a,w/2,color);circle(b,w/2,color)
        highlight = tuple(round(n*.6+255*.4) for n in color)
        draw.line((a,b),fill=highlight,width=max(1,round(w*.42)))
    def prop(item):
        a, b, kind = item['a'], item['b'], item['kind']
        if kind == 'MAT':
            draw.polygon([project(add(a,[0,0,-.55])),project(add(b,[0,0,-.55])),
                          project(add(b,[0,0,.55])),project(add(a,[0,0,.55]))],fill=(200,209,186))
        elif kind == 'BENCH':
            rod([a[0],.035,a[2]],a,.055,(171,180,167))
            rod([b[0],.035,b[2]],b,.055,(171,180,167))
            for pt in (a,b):rod([pt[0],.035,pt[2]-.32],[pt[0],.035,pt[2]+.32],.045,(105,116,108))
            rod(a,b,.14,(79,92,72))
        elif kind in ('RAIL','BAR'): rod(a,b,.04,(105,116,108))
        elif kind in ('PAD','ROLLER'): rod(a,b,.13,(83,97,73))
        elif kind == 'CABLE':
            rod([a[0],.03,a[2]],[a[0],max(a[1]+.15,1.8),a[2]],.045,(171,180,167))
            circle(project(a),.055*scale,(105,116,108))
            rod(a,b,.014,(105,116,108),False)
        elif kind == 'ROPE':
            rod(a,b,.026,(95,99,82));circle(project(b),.04*scale,(61,73,52))
        elif kind in ('BARBELL','DUMBBELL'):
            rod(a,b,.025,(105,116,108))
            delta=sub(b,a);length=math.sqrt(sum(n*n for n in delta));axis=mul(delta,1/length)
            radius = .22 if kind == 'BARBELL' else .125
            for center in (add(a,mul(axis,.05)),add(b,mul(axis,-.05))):
                x,y=project(center)
                draw.ellipse((x-radius*scale*.30,y-radius*scale,x+radius*scale*.30,y+radius*scale),fill=(52,67,46))
                draw.ellipse((x-radius*scale*.21,y-radius*scale*.86,x+radius*scale*.21,y+radius*scale*.86),fill=(102,129,77))
                circle((x,y),max(1,.023*scale),(184,199,159))
    floor=project([0,0,0])
    draw.ellipse((floor[0]-scale*.79,floor[1]-scale*.10,floor[0]+scale*.79,floor[1]+scale*.07),fill=(213,220,204))
    for i in range(-3,4):
        draw.line((project([i*.4,0,-1.1]),project([i*.4,0,1.1])),fill=(221,226,215))
        draw.line((project([-1.3,0,i*.35]),project([1.3,0,i*.35])),fill=(221,226,215))
    props = frame['props']
    for item in props:
        if item['kind'] not in ('DUMBBELL','BARBELL','BAR') or (guide['key']=='squat' and item['kind']=='BARBELL'):prop(item)
    joints=frame['joints'];layers=[]
    muscle=guide['muscle']
    highlights={'腿部','背部'} if guide['key'] in ('db_deadlift','rdl') else {muscle}
    def limb(a,b,w,active):
        pa,pb=joints[a],joints[b]
        def paint():
            rod(pa,pb,w,BODY)
            if active:rod(lerp(pa,pb,.2),lerp(pa,pb,.7),w*.64,ACTIVE,False)
            circle(project(pb),w*scale*.29,EDGE)
            circle(project(pb),w*scale*.2,(220,224,214))
        layers.append((depth(lerp(pa,pb,.5)),paint))
    for side in ('L','R'):
        limb('HIP_'+side,'KNEE_'+side,.15,'腿部' in highlights)
        limb('KNEE_'+side,'ANKLE_'+side,.105,'小腿' in highlights)
        ankle,toe=joints['ANKLE_'+side],joints['TOE_'+side]
        layers.append((depth(ankle),lambda a=ankle,b=toe:rod(a,b,.085,(86,99,78))))
        limb('SHOULDER_'+side,'ELBOW_'+side,.105,muscle=='手臂')
        limb('ELBOW_'+side,'WRIST_'+side,.079,False)
        wrist=joints['WRIST_'+side]
        layers.append((depth(wrist),lambda a=wrist:circle(project(a),.045*scale,BODY)))
    def torso():
        hip,chest=project(joints['PELVIS']),project(joints['CHEST'])
        dx,dy=chest[0]-hip[0],chest[1]-hip[1];length=math.hypot(dx,dy)
        normal=(-dy/length,dx/length)
        sl,sr=project(joints['SHOULDER_L']),project(joints['SHOULDER_R'])
        top=.22*scale+math.hypot(sl[0]-sr[0],sl[1]-sr[1])*.55
        bottom=(.23+.11*abs(math.sin(yaw)))*scale
        def at(pt,d):return pt[0]+normal[0]*d,pt[1]+normal[1]*d
        draw.polygon([at(chest,top/2),at(chest,-top/2),at(hip,-bottom/2),at(hip,bottom/2)],fill=BODY,outline=EDGE,width=1)
        a,b=joints['PELVIS'],joints['CHEST']
        if highlights.intersection({'胸部','背部','腹部'}):
            rod(lerp(a,b,.25 if muscle=='腹部' else .57),lerp(a,b,.66 if muscle=='腹部' else .86),
                .14 if muscle=='腹部' else .20,ACTIVE,False)
        rod(a,lerp(a,b,.12),.25,ACTIVE if '腿部' in highlights else (93,109,81))
        rod(b,joints['NECK'],.10,BODY)
        head=project(joints['HEAD']);circle(head,.115*scale,EDGE);circle(head,.108*scale,(220,226,213))
        spine=sub(b,a);mag=math.sqrt(sum(n*n for n in spine));spine=mul(spine,1/mag)
        side=sub(joints['SHOULDER_L'],joints['SHOULDER_R']);mag=math.sqrt(sum(n*n for n in side));side=mul(side,1/mag)
        face=[spine[1]*side[2]-spine[2]*side[1],spine[2]*side[0]-spine[0]*side[2],spine[0]*side[1]-spine[1]*side[0]]
        mag=math.sqrt(sum(n*n for n in face));face=mul(face,1/mag)
        if guide['key'] in ('bench','db_bench','incline_bench','fly','lying_triceps','bridge','crunch','reverse_crunch'):face=mul(face,-1)
        circle(project(add(joints['HEAD'],mul(face,.10))),.022*scale,(172,182,159))
        for joint in ('SHOULDER_L','SHOULDER_R'):circle(project(joints[joint]),.065*scale,ACTIVE if muscle=='肩部' else BODY)
    layers.append((depth(joints['CHEST']),torso))
    for _,paint in sorted(layers,key=lambda item:item[0]):paint()
    for item in props:
        if item['kind'] in ('DUMBBELL','BARBELL','BAR') and not (guide['key']=='squat' and item['kind']=='BARBELL'):prop(item)
    return image


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--report',type=Path,default=ROOT/'android/app/build/reports/motion-keyframes.json')
    parser.add_argument('--output',type=Path,default=ROOT/'docs/motion')
    args=parser.parse_args()
    guides=json.loads(args.report.read_text(encoding='utf-8'))
    args.output.mkdir(parents=True,exist_ok=True)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
    small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',14)
    for page in range(math.ceil(len(guides)/8)):
        rows=guides[page*8:page*8+8]
        sheet=Image.new('RGB',(1050,60+len(rows)*260),(250,251,247));d=ImageDraw.Draw(sheet)
        d.text((18,15),f'独练 · 原创动作姿态检查 {page+1}（引擎关键帧，非真机截图）',fill=(32,38,31),font=font)
        for row,guide in enumerate(rows):
            y=60+row*260
            for col,frame in enumerate(guide['frames']):
                sheet.paste(render(frame,guide), (col*350,y+35))
                d.text((col*350+10,y+10),guide['name']+' · '+('起始','中段','关键姿势')[col],fill=(32,38,31),font=small)
        file=args.output/f'poses-{page+1}.png';sheet.save(file)
        print(file)
    chosen=[next(g for g in guides if g['key']==key) for key in ('bench','squat','rdl','pushup','pulldown','shoulder_press')]
    if all('playback' in guide for guide in chosen):
        frames=[]
        for i in range(len(chosen[0]['playback'])):
            canvas=Image.new('RGB',(1050,570),(250,251,247));d=ImageDraw.Draw(canvas)
            d.text((16,8),'独练 · 动作路径预览（引擎实际帧，非真机截图）',fill=(32,38,31),font=font)
            for index,guide in enumerate(chosen):
                x=(index%3)*350;y=40+(index//3)*265
                canvas.paste(render(guide['playback'][i],guide),(x,y+30))
                d.text((x+12,y+4),guide['name'],fill=(32,38,31),font=font)
            frames.append(canvas)
        out=args.output/'motion-preview.gif'
        frames[0].save(out,save_all=True,append_images=frames[1:],duration=round(5500/len(frames)),loop=0,disposal=2)
        print(out)
        still=args.output/"motion-preview-keyframe.png"
        frames[len(frames)//2].save(still)
        print(still)


if __name__=='__main__':main()
